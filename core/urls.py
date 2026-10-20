from django.urls import path
from vacinas import views 
from django.contrib.auth import views as auth_views


urlpatterns = [
    path('admin/', views.configuracoes, name='configuracoes'),
    path('manifest.webmanifest', views.manifest, name='manifest'),
    path('sw.js', views.service_worker, name='service_worker'),
    path('', views.index_escolha, name='index_escolha'),
    path('dashboard/', views.home, name='home'), 
    path('pacientes/novo/', views.cadastrar_paciente, name='cadastrar_paciente'),
    path('registrar-dose/', views.registrar_dose, name='registrar_dose'),
    path('pacientes/', views.listar_pacientes, name='listar_pacientes'),
    path('editar-paciente/<int:pk>/', views.editar_paciente, name='editar_paciente'),
    path('portal/', views.portal_boas_vindas, name='portal_boas_vindas'),
    path('api/portal/chat/', views.portal_chat, name='portal_chat'),
    path('caderneta/', views.caderneta_paciente, name='caderneta_paciente'),
    path('postos/', views.busca_postos, name='busca_postos'),
    path('login/', auth_views.LoginView.as_view(template_name='vacinas/login.html'), name='login'),
    path('logout/', auth_views.LogoutView.as_view(), name='logout'),
    path('estoque/', views.listar_estoque, name='listar_estoque'),
    path('estoque/novo/', views.cadastrar_estoque, name='cadastrar_estoque'),
    path('exportar-csv/', views.exportar_dados_csv, name='exportar_csv'),
    path('ajax/carregar-vacinas/', views.carregar_vacinas_posto, name='ajax_carregar_vacinas'),
    path('api/offline/bootstrap/', views.offline_bootstrap, name='offline_bootstrap'),
    path('agendar/', views.agendar_vacina, name='agendar_vacina'),
    path('agendar/sucesso/', views.index_escolha, name='sucesso_agendamento'),
    path('agendamentos/listar/', views.listar_agendamentos, name='listar_agendamentos'),
    path('meus-agendamentos/', views.meus_agendamentos, name='meus_agendamentos'),
    path('agendamentos/confirmar/<int:agendamento_id>/', views.confirmar_agendamento, name='confirmar_agendamento'),
    path('agendamentos/historico/', views.historico_agendamentos, name='historico_agendamentos'),
    path('agendamentos/cancelar/<int:agendamento_id>/', views.cancelar_agendamento, name='cancelar_agendamento'),
]
