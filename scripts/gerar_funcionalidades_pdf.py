#!/usr/bin/env python3
"""Gera o PDF de funcionalidades do EasyVacc."""

from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.platypus import (
    HRFlowable,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

BASE_DIR = Path(__file__).resolve().parent.parent
OUTPUT = BASE_DIR / "docs" / "EasyVacc_Funcionalidades.pdf"

PRIMARY = colors.HexColor("#2563eb")
PRIMARY_DARK = colors.HexColor("#1e40af")
ACCENT = colors.HexColor("#059669")
LIGHT_BG = colors.HexColor("#f8fafc")
TEXT = colors.HexColor("#1e293b")
MUTED = colors.HexColor("#64748b")
BORDER = colors.HexColor("#e2e8f0")

YES = colors.HexColor("#059669")
PARTIAL = colors.HexColor("#d97706")
NO = colors.HexColor("#dc2626")


def status_cell(value: str) -> str:
    mapping = {
        "sim": ("✓ Sim", YES),
        "parcial": ("~ Parcial", PARTIAL),
        "nao": ("✗ Não", NO),
    }
    label, color = mapping.get(value.lower(), (value, TEXT))
    hex_color = color.hexval() if hasattr(color, "hexval") else str(color)
    return f'<font color="{hex_color}"><b>{label}</b></font>'


SECTIONS = [
    {
        "title": "Acesso e autenticação",
        "rows": [
            ("Tela inicial — escolha Servidor Público / Portal do Cidadão", "sim", "sim", "sim", "Melhorar textos de onboarding"),
            ("Login de profissionais de saúde", "sim", "sim", "sim", "Recuperação de senha e perfis de acesso"),
            ("Logout", "sim", "sim", "sim", "—"),
            ("Painel administrativo Django (/admin)", "sim", "sim", "sim", "Incluir Agendamento no admin"),
        ],
    },
    {
        "title": "Painel do servidor (área restrita)",
        "rows": [
            ("Dashboard com totais e últimas aplicações", "sim", "sim", "sim", "Gráficos e filtros por período"),
            ("Cadastro de pacientes", "sim", "sim", "sim", "Validação automática de CPF/CNS"),
            ("Edição de pacientes", "sim", "sim", "sim", "Histórico de alterações"),
            ("Listagem de pacientes e dependentes", "sim", "sim", "sim", "Busca, paginação e exportação"),
            ("Registro de dose aplicada", "sim", "sim", "sim", "—"),
            ("Gestão de estoque (listar e cadastrar lotes)", "sim", "sim", "sim", "Alertas de validade e estoque mínimo"),
            ("Baixa automática de estoque ao aplicar dose", "sim", "sim", "sim", "—"),
            ("Exportação de relatório em CSV", "sim", "sim", "sim", "Nome do arquivo ainda fixo em “Saquarema”"),
            ("Carregamento dinâmico de vacinas por posto (AJAX)", "sim", "sim", "sim", "—"),
        ],
    },
    {
        "title": "Portal do cidadão (área pública)",
        "rows": [
            ("Página de boas-vindas do portal", "sim", "sim", "sim", "—"),
            ("Caderneta digital — consulta de doses aplicadas", "sim", "sim", "sim", "Consulta só por nome/CPF/data (sem login)"),
            ("Busca de postos de saúde por CEP (ViaCEP)", "sim", "sim", "sim", "Depende de postos cadastrados no sistema"),
            ("Visualização de vacinas disponíveis por posto", "sim", "sim", "sim", "Atualização em tempo real limitada"),
            ("Agendamento online de vacina", "sim", "sim", "sim", "Confirmação por e-mail ou SMS"),
            ("Consulta “Meus agendamentos”", "sim", "sim", "sim", "Lembretes automáticos de data/hora"),
            ("Chatbot educativo sobre vacinas", "sim", "parcial", "sim", "IA completa exige GROQ_API_KEY ou OPENAI_API_KEY"),
        ],
    },
    {
        "title": "Agendamentos (fluxo operacional)",
        "rows": [
            ("Reserva de dose no estoque ao agendar", "sim", "sim", "sim", "—"),
            ("Listagem de agendamentos pendentes", "sim", "sim", "sim", "Filtros por posto e data"),
            ("Confirmar agendamento e registrar dose", "sim", "sim", "sim", "—"),
            ("Cancelar agendamento com justificativa", "sim", "sim", "sim", "Remover código duplicado na view"),
            ("Histórico de agendamentos concluídos/cancelados", "sim", "sim", "sim", "Restringir acesso apenas a profissionais"),
            ("Status: pendente, concluído, cancelado, ausente", "sim", "parcial", "sim", "Status “ausente” ainda sem fluxo dedicado"),
        ],
    },
    {
        "title": "PWA, offline e mobile",
        "rows": [
            ("Instalação como app (PWA / manifest)", "sim", "sim", "sim", "—"),
            ("Service Worker na raiz do site", "sim", "sim", "sim", "—"),
            ("Sincronização offline (IndexedDB + fila)", "sim", "sim", "sim", "Testes automatizados do fluxo offline"),
            ("Formulários compatíveis com sync offline", "sim", "sim", "sim", "Estender a mais telas"),
            ("Guia de instalação do PWA", "sim", "sim", "sim", "—"),
        ],
    },
    {
        "title": "Infraestrutura e dados",
        "rows": [
            ("Banco SQLite para desenvolvimento local", "sim", "sim", "sim", "—"),
            ("Integração PostgreSQL / Supabase em produção", "sim", "parcial", "sim", "Exige arquivo .env configurado"),
            ("Deploy na Vercel", "sim", "parcial", "sim", "Validar variáveis e migrations em produção"),
            ("Arquivos estáticos com WhiteNoise", "sim", "sim", "sim", "—"),
            ("Testes automatizados", "nao", "nao", "sim", "Criar suite de testes (models, views, APIs)"),
        ],
    },
]


def build_pdf():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)

    doc = SimpleDocTemplate(
        str(OUTPUT),
        pagesize=A4,
        leftMargin=1.6 * cm,
        rightMargin=1.6 * cm,
        topMargin=1.5 * cm,
        bottomMargin=1.5 * cm,
        title="EasyVacc — Mapa de Funcionalidades",
        author="EasyVacc / Univassouras",
    )

    styles = getSampleStyleSheet()
    title_style = ParagraphStyle(
        "DocTitle",
        parent=styles["Title"],
        fontName="Helvetica-Bold",
        fontSize=26,
        textColor=PRIMARY_DARK,
        spaceAfter=6,
        alignment=TA_CENTER,
        leading=30,
    )
    subtitle_style = ParagraphStyle(
        "DocSubtitle",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=11,
        textColor=MUTED,
        alignment=TA_CENTER,
        spaceAfter=4,
    )
    section_style = ParagraphStyle(
        "Section",
        parent=styles["Heading2"],
        fontName="Helvetica-Bold",
        fontSize=13,
        textColor=PRIMARY_DARK,
        spaceBefore=14,
        spaceAfter=8,
    )
    body_style = ParagraphStyle(
        "Body",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=9.5,
        textColor=TEXT,
        leading=13,
    )
    legend_style = ParagraphStyle(
        "Legend",
        parent=body_style,
        fontSize=9,
        textColor=MUTED,
        leading=12,
    )
    cell_style = ParagraphStyle(
        "Cell",
        parent=body_style,
        fontSize=8.5,
        leading=11,
    )
    header_style = ParagraphStyle(
        "Header",
        parent=cell_style,
        fontName="Helvetica-Bold",
        textColor=colors.white,
        fontSize=8.5,
    )

    story = []

    # Capa
    story.append(Spacer(1, 2.2 * cm))
    story.append(Paragraph("EasyVacc", title_style))
    story.append(Paragraph("Sistema de Controle de Vacinas", subtitle_style))
    story.append(Spacer(1, 0.3 * cm))
    story.append(
        HRFlowable(
            width="30%",
            thickness=3,
            color=PRIMARY,
            spaceBefore=6,
            spaceAfter=18,
            hAlign="CENTER",
        )
    )
    story.append(Paragraph("Mapa de Funcionalidades", ParagraphStyle(
        "MapTitle",
        parent=title_style,
        fontSize=18,
        textColor=TEXT,
    )))
    story.append(Spacer(1, 0.4 * cm))
    story.append(Paragraph(
        "Universidade de Vassouras · Saquarema/RJ<br/>"
        "Documento gerado para acompanhamento do produto",
        subtitle_style,
    ))
    story.append(Spacer(1, 1.2 * cm))

    legend_data = [
        [
            Paragraph("<b>Legenda rápida</b>", body_style),
            Paragraph(f'{status_cell("sim")} — implementado e operacional', legend_style),
            Paragraph(f'{status_cell("parcial")} — existe, mas com limitações', legend_style),
            Paragraph(f'{status_cell("nao")} — ainda não implementado', legend_style),
        ]
    ]
    legend_table = Table(legend_data, colWidths=[3.2 * cm, 4.3 * cm, 4.5 * cm, 4.5 * cm])
    legend_table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), LIGHT_BG),
        ("BOX", (0, 0), (-1, -1), 0.5, BORDER),
        ("INNERGRID", (0, 0), (-1, -1), 0.25, BORDER),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("TOPPADDING", (0, 0), (-1, -1), 8),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
        ("LEFTPADDING", (0, 0), (-1, -1), 10),
    ]))
    story.append(legend_table)
    story.append(Spacer(1, 0.6 * cm))

    intro = (
        "Este documento resume o que o sistema <b>possui hoje</b>, o que <b>funciona</b>, "
        "o que <b>será mantido</b> no roadmap e onde <b>precisa melhorar</b>. "
        "Organizado por módulo, sem burocracia — pensado para leitura rápida pela equipe."
    )
    story.append(Paragraph(intro, body_style))
    story.append(PageBreak())

    col_widths = [6.8 * cm, 1.55 * cm, 1.55 * cm, 1.85 * cm, 5.0 * cm]
    headers = [
        Paragraph("Funcionalidade", header_style),
        Paragraph("Existe", header_style),
        Paragraph("Funciona", header_style),
        Paragraph("Será<br/>mantida", header_style),
        Paragraph("Precisa melhorar", header_style),
    ]

    for section in SECTIONS:
        story.append(Paragraph(section["title"], section_style))
        table_data = [headers]
        for name, exists, works, kept, improve in section["rows"]:
            improve_text = "—" if improve.strip() == "—" else improve
            table_data.append([
                Paragraph(name, cell_style),
                Paragraph(status_cell(exists), cell_style),
                Paragraph(status_cell(works), cell_style),
                Paragraph(status_cell(kept), cell_style),
                Paragraph(improve_text, cell_style),
            ])

        table = Table(table_data, colWidths=col_widths, repeatRows=1)
        table.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), PRIMARY),
            ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
            ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
            ("FONTSIZE", (0, 0), (-1, 0), 8.5),
            ("BACKGROUND", (0, 1), (-1, -1), colors.white),
            ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, LIGHT_BG]),
            ("BOX", (0, 0), (-1, -1), 0.5, BORDER),
            ("INNERGRID", (0, 0), (-1, -1), 0.25, BORDER),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("TOPPADDING", (0, 0), (-1, -1), 7),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
            ("LEFTPADDING", (0, 0), (-1, -1), 7),
            ("RIGHTPADDING", (0, 0), (-1, -1), 7),
            ("ALIGN", (1, 1), (3, -1), "CENTER"),
        ]))
        story.append(table)
        story.append(Spacer(1, 0.35 * cm))

    story.append(Spacer(1, 0.5 * cm))
    story.append(HRFlowable(width="100%", thickness=1, color=BORDER, spaceAfter=10))
    summary = (
        "<b>Resumo:</b> o EasyVacc cobre o ciclo principal — cadastro, estoque, aplicação, "
        "agendamento e consulta pelo cidadão — com diferencial de PWA e modo offline. "
        "Prioridades sugeridas: testes automatizados, segurança nas consultas públicas, "
        "configuração de produção (Supabase/Vercel) e evolução do chatbot com chave de IA."
    )
    story.append(Paragraph(summary, body_style))

    doc.build(story)
    return OUTPUT


if __name__ == "__main__":
    path = build_pdf()
    print(f"PDF gerado: {path}")
