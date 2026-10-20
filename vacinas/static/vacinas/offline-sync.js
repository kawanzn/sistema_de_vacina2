(function () {
  'use strict';

  var DB_NAME = 'easyvacc-offline-v1';
  var DB_VERSION = 1;
  var QUEUE_STORE = 'queue';
  var META_STORE = 'meta';
  var BOOTSTRAP_URL = '/api/offline/bootstrap/';
  var FORM_SELECTOR = 'form[data-offline-sync]';
  var dbPromise = null;
  var syncing = false;

  function openDb() {
    if (dbPromise) return dbPromise;
    dbPromise = new Promise(function (resolve, reject) {
      var req = indexedDB.open(DB_NAME, DB_VERSION);
      req.onupgradeneeded = function () {
        var db = req.result;
        if (!db.objectStoreNames.contains(QUEUE_STORE)) {
          var queue = db.createObjectStore(QUEUE_STORE, { keyPath: 'id' });
          queue.createIndex('createdAt', 'createdAt');
        }
        if (!db.objectStoreNames.contains(META_STORE)) {
          db.createObjectStore(META_STORE, { keyPath: 'key' });
        }
      };
      req.onsuccess = function () { resolve(req.result); };
      req.onerror = function () { reject(req.error); };
    });
    return dbPromise;
  }

  function storeRequest(mode, storeName, callback) {
    return openDb().then(function (db) {
      return new Promise(function (resolve, reject) {
        var tx = db.transaction(storeName, mode);
        var store = tx.objectStore(storeName);
        var result;
        try {
          result = callback(store);
        } catch (err) {
          reject(err);
          return;
        }
        tx.oncomplete = function () { resolve(result); };
        tx.onerror = function () { reject(tx.error); };
      });
    });
  }

  function requestToPromise(req) {
    return new Promise(function (resolve, reject) {
      req.onsuccess = function () { resolve(req.result); };
      req.onerror = function () { reject(req.error); };
    });
  }

  function uuid() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return 'offline-' + Date.now() + '-' + Math.random().toString(16).slice(2);
  }

  function getCookie(name) {
    var parts = document.cookie ? document.cookie.split(';') : [];
    for (var i = 0; i < parts.length; i += 1) {
      var pair = parts[i].trim();
      if (pair.indexOf(name + '=') === 0) {
        return decodeURIComponent(pair.slice(name.length + 1));
      }
    }
    return '';
  }

  function syncHeaders(extra) {
    var headers = extra || {};
    var csrf = getCookie('csrftoken');
    if (csrf) headers['X-CSRFToken'] = csrf;
    return headers;
  }

  function ensureClientId(form) {
    var input = form.querySelector('input[name="offline_client_id"]');
    if (!input) {
      input = document.createElement('input');
      input.type = 'hidden';
      input.name = 'offline_client_id';
      form.appendChild(input);
    }
    if (!input.value) input.value = uuid();
    return input.value;
  }

  function clearClientId(form) {
    var input = form.querySelector('input[name="offline_client_id"]');
    if (input) input.remove();
  }

  function serializeForm(form) {
    var formData = new FormData(form);
    var entries = [];
    formData.forEach(function (value, key) {
      if (value instanceof File) return;
      entries.push([key, value]);
    });
    return {
      id: formData.get('offline_client_id') || uuid(),
      url: form.action || window.location.href,
      method: (form.method || 'POST').toUpperCase(),
      label: form.getAttribute('data-offline-sync') || 'registro',
      entries: entries,
      createdAt: Date.now(),
      lastError: '',
    };
  }

  function formDataFromEntries(entries) {
    var formData = new FormData();
    entries.forEach(function (entry) {
      if (entry[0] === 'csrfmiddlewaretoken') return;
      formData.append(entry[0], entry[1]);
    });
    var csrf = getCookie('csrftoken');
    if (csrf) formData.append('csrfmiddlewaretoken', csrf);
    return formData;
  }

  function putMeta(key, value) {
    return storeRequest('readwrite', META_STORE, function (store) {
      store.put({ key: key, value: value, updatedAt: Date.now() });
    });
  }

  function getMeta(key) {
    return openDb().then(function (db) {
      var tx = db.transaction(META_STORE, 'readonly');
      return requestToPromise(tx.objectStore(META_STORE).get(key)).then(function (row) {
        return row ? row.value : null;
      });
    });
  }

  function getQueue() {
    return openDb().then(function (db) {
      var tx = db.transaction(QUEUE_STORE, 'readonly');
      return requestToPromise(tx.objectStore(QUEUE_STORE).getAll()).then(function (items) {
        return items.sort(function (a, b) { return a.createdAt - b.createdAt; });
      });
    });
  }

  function enqueue(form) {
    ensureClientId(form);
    var item = serializeForm(form);
    return storeRequest('readwrite', QUEUE_STORE, function (store) {
      store.put(item);
    }).then(function () {
      updateStatus();
      return item;
    });
  }

  function removeQueued(id) {
    return storeRequest('readwrite', QUEUE_STORE, function (store) {
      store.delete(id);
    });
  }

  function markError(id, message) {
    return openDb().then(function (db) {
      return new Promise(function (resolve, reject) {
        var tx = db.transaction(QUEUE_STORE, 'readwrite');
        var store = tx.objectStore(QUEUE_STORE);
        var req = store.get(id);
        req.onsuccess = function () {
          var item = req.result;
          if (item) {
            item.lastError = message || 'Falha ao sincronizar.';
            item.lastAttemptAt = Date.now();
            store.put(item);
          }
        };
        tx.oncomplete = resolve;
        tx.onerror = function () { reject(tx.error); };
      });
    });
  }

  function injectStyles() {
    if (document.getElementById('easyvacc-offline-style')) return;
    var style = document.createElement('style');
    style.id = 'easyvacc-offline-style';
    style.textContent = [
      '#easyvacc-offline-status{position:fixed;top:.75rem;right:.75rem;z-index:1000000;display:none;align-items:center;gap:.5rem;border-radius:999px;padding:.45rem .8rem;font:600 .78rem Inter,system-ui,sans-serif;box-shadow:0 10px 25px rgba(15,23,42,.14);border:1px solid #e2e8f0;background:#fff;color:#334155}',
      '#easyvacc-offline-status.is-offline{display:flex;background:#fff7ed;color:#9a3412;border-color:#fed7aa}',
      '#easyvacc-offline-status.has-pending{display:flex;background:#eff6ff;color:#1d4ed8;border-color:#bfdbfe}',
      '#easyvacc-offline-flash{position:fixed;left:50%;bottom:1rem;transform:translateX(-50%);z-index:1000001;max-width:min(92vw,28rem);border-radius:.75rem;padding:.75rem 1rem;font:600 .85rem Inter,system-ui,sans-serif;box-shadow:0 16px 35px rgba(15,23,42,.18);background:#0f172a;color:#fff}',
      '#easyvacc-offline-flash.error{background:#991b1b}',
      '#easyvacc-offline-flash.success{background:#166534}',
      '@media (max-width:991px){#easyvacc-offline-status{top:.5rem;right:.5rem;font-size:.72rem}#easyvacc-offline-flash{bottom:6rem}}'
    ].join('');
    document.head.appendChild(style);
  }

  function getStatusEl() {
    injectStyles();
    var el = document.getElementById('easyvacc-offline-status');
    if (!el) {
      el = document.createElement('div');
      el.id = 'easyvacc-offline-status';
      el.setAttribute('role', 'status');
      document.body.appendChild(el);
    }
    return el;
  }

  function flash(message, type) {
    injectStyles();
    var old = document.getElementById('easyvacc-offline-flash');
    if (old) old.remove();
    var el = document.createElement('div');
    el.id = 'easyvacc-offline-flash';
    if (type) el.className = type;
    el.textContent = message;
    document.body.appendChild(el);
    window.setTimeout(function () {
      if (el.parentNode) el.remove();
    }, 5200);
  }

  function updateStatus() {
    return getQueue().then(function (items) {
      var el = getStatusEl();
      var count = items.length;
      el.className = '';
      if (!navigator.onLine) {
        el.classList.add('is-offline');
        el.textContent = count ? 'Offline: ' + count + ' pendente(s)' : 'Offline';
      } else if (count) {
        el.classList.add('has-pending');
        el.textContent = syncing ? 'Sincronizando ' + count + ' pendente(s)' : count + ' pendente(s) para enviar';
      } else {
        el.style.display = 'none';
        return;
      }
      el.style.display = 'flex';
    }).catch(function () {});
  }

  function submitQueuedItem(item) {
    return fetch(item.url, {
      method: item.method || 'POST',
      body: formDataFromEntries(item.entries),
      credentials: 'same-origin',
      headers: syncHeaders({
        'X-Offline-Replay': '1',
        'X-Requested-With': 'XMLHttpRequest',
      }),
    }).then(function (response) {
      var contentType = response.headers.get('content-type') || '';
      if (response.redirected || contentType.indexOf('application/json') === -1) {
        return { ok: false, message: 'Envio não confirmado. Entre novamente e tente sincronizar.', status: response.status };
      }
      return response.json().catch(function () {
        return null;
      }).then(function (data) {
        if (response.ok && data && data.ok === true) return { ok: true };
        return {
          ok: false,
          message: data && data.message ? data.message : 'Falha ao sincronizar.',
          status: response.status,
        };
      });
    });
  }

  function syncQueue() {
    if (syncing || !navigator.onLine) {
      updateStatus();
      return Promise.resolve();
    }
    syncing = true;
    updateStatus();
    return getQueue().then(function (items) {
      var chain = Promise.resolve();
      items.forEach(function (item) {
        chain = chain.then(function () {
          return submitQueuedItem(item).then(function (result) {
            if (result.ok) {
              return removeQueued(item.id);
            }
            return markError(item.id, result.message);
          }).catch(function (err) {
            return markError(item.id, err && err.message ? err.message : 'Sem conexao.');
          });
        });
      });
      return chain;
    }).then(function () {
      syncing = false;
      updateStatus();
      refreshReferenceData();
    }).catch(function () {
      syncing = false;
      updateStatus();
    });
  }

  function refreshReferenceData() {
    if (!navigator.onLine) return Promise.resolve();
    return fetch(BOOTSTRAP_URL, {
      credentials: 'same-origin',
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    }).then(function (response) {
      if (!response.ok) throw new Error('bootstrap');
      return response.json();
    }).then(function (data) {
      return putMeta('bootstrap', data);
    }).catch(function () {});
  }

  function getReferenceData() {
    return getMeta('bootstrap').then(function (data) {
      return data || { postos: [], estoques: [], pacientes: [] };
    });
  }

  function resolveElement(value) {
    if (!value) return null;
    if (typeof value === 'string') return document.querySelector(value);
    return value;
  }

  function populateVacinasSelect(postoInput, vacinaInput) {
    var postoSelect = resolveElement(postoInput);
    var vacinaSelect = resolveElement(vacinaInput);
    if (!postoSelect || !vacinaSelect) return Promise.resolve(false);
    var postoId = String(postoSelect.value || '');
    if (!postoId) return Promise.resolve(false);

    return getReferenceData().then(function (data) {
      var items = (data.estoques || []).filter(function (item) {
        return String(item.posto_id || '') === postoId && Number(item.disponivel || 0) > 0;
      });
      if (!items.length) return false;
      vacinaSelect.innerHTML = '<option value="">Selecione a vacina...</option>';
      items.forEach(function (item) {
        var option = document.createElement('option');
        option.value = item.id;
        option.textContent = item.nome_vacina + ' - Lote: ' + item.lote;
        vacinaSelect.appendChild(option);
      });
      return true;
    });
  }

  function bindDependentSelects() {
    var pairs = [
      ['select[name="posto_selecionado"]', 'select[name="item_estoque"]'],
      ['select[name="posto"]', 'select[name="item_estoque"]'],
    ];
    pairs.forEach(function (pair) {
      var postoSelect = document.querySelector(pair[0]);
      var vacinaSelect = document.querySelector(pair[1]);
      if (!postoSelect || !vacinaSelect || postoSelect.dataset.offlineBound) return;
      postoSelect.dataset.offlineBound = '1';
      postoSelect.addEventListener('change', function () {
        window.setTimeout(function () {
          if (!navigator.onLine || vacinaSelect.options.length <= 1) {
            populateVacinasSelect(postoSelect, vacinaSelect);
          }
        }, 450);
      });
    });
  }

  function handleOfflineQueued(form) {
    flash('Sem rede: registro guardado neste aparelho para enviar depois.', 'success');
    form.reset();
    clearClientId(form);
    updateStatus();
  }

  function bindForms() {
    document.addEventListener('submit', function (event) {
      var form = event.target.closest(FORM_SELECTOR);
      if (!form) return;
      if ((form.enctype || '').toLowerCase().indexOf('multipart') !== -1) return;

      event.preventDefault();
      ensureClientId(form);

      var submitter = event.submitter || form.querySelector('[type="submit"]');
      if (submitter) submitter.disabled = true;

      if (!navigator.onLine) {
        enqueue(form).then(function () {
          handleOfflineQueued(form);
        }).catch(function () {
          flash('Não foi possível guardar neste aparelho. Os campos foram preservados; tente novamente.', 'error');
        }).finally(function () {
          if (submitter) submitter.disabled = false;
        });
        return;
      }

      fetch(form.action || window.location.href, {
        method: (form.method || 'POST').toUpperCase(),
        body: new FormData(form),
        credentials: 'same-origin',
        headers: syncHeaders({ 'X-Requested-With': 'XMLHttpRequest' }),
      }).then(function (response) {
        if (response.redirected) {
          window.location.href = response.url;
          return null;
        }
        var contentType = response.headers.get('content-type') || '';
        if (contentType.indexOf('application/json') !== -1) {
          return response.json().then(function (data) {
            if (!response.ok || data.ok === false) {
              flash(data.message || 'Nao foi possivel salvar.', 'error');
              return;
            }
            if (data.redirect_url) {
              window.location.href = data.redirect_url;
              return;
            }
            flash(data.message || 'Salvo com sucesso.', 'success');
          });
        }
        return response.text().then(function (html) {
          document.open();
          document.write(html);
          document.close();
        });
      }).catch(function () {
        return enqueue(form).then(function () {
          handleOfflineQueued(form);
        }).catch(function () {
          flash('Não foi possível guardar neste aparelho. Os campos foram preservados; tente novamente.', 'error');
        });
      }).finally(function () {
        if (submitter) submitter.disabled = false;
      });
    });
  }

  window.easyVaccOfflineSync = {
    syncQueue: syncQueue,
    refreshReferenceData: refreshReferenceData,
    populateVacinasSelect: populateVacinasSelect,
    getReferenceData: getReferenceData,
  };

  document.addEventListener('DOMContentLoaded', function () {
    injectStyles();
    bindForms();
    bindDependentSelects();
    updateStatus();
    refreshReferenceData().then(syncQueue);
  });

  window.addEventListener('online', function () {
    flash('Conexao voltou. Sincronizando pendencias...', 'success');
    refreshReferenceData().then(syncQueue);
  });
  window.addEventListener('offline', updateStatus);
})();
