#!/usr/bin/env python
# -*- coding: utf-8 -*-

import json
from pathlib import Path

from reportlab.lib.pagesizes import A4
from reportlab.lib.colors import HexColor, black, white
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics

PAGE_WIDTH, PAGE_HEIGHT = A4

MARGIN_X = 40
MARGIN_TOP = 40

BLUE = HexColor("#2563eb")
BLUE_DARK = HexColor("#1d4ed8")
LIGHT_BLUE = HexColor("#eff6ff")
GRAY_TEXT = HexColor("#4b5563")
LIGHT_GRAY = HexColor("#e5e7eb")


# -----------------------------
#  Utilitários de texto
# -----------------------------

def wrap_text(text, font_name, font_size, max_width):
    words = text.split()
    if not words:
        return []

    lines = []
    current_line = words[0]

    for word in words[1:]:
        test_line = current_line + " " + word
        w = pdfmetrics.stringWidth(test_line, font_name, font_size)
        if w <= max_width:
            current_line = test_line
        else:
            lines.append(current_line)
            current_line = word

    lines.append(current_line)
    return lines


def draw_paragraph(c, text, x, y, max_width,
                   font_name="Helvetica",
                   font_size=12,
                   leading=None,
                   color=black):
    if leading is None:
        leading = font_size * 1.3

    lines = wrap_text(text, font_name, font_size, max_width)
    c.setFillColor(color)
    c.setFont(font_name, font_size)

    current_y = y
    for line in lines:
        c.drawString(x, current_y, line)
        current_y -= leading

    return current_y


# -----------------------------
#  Capa
# -----------------------------

def desenhar_capa(c, mes="Dezembro", ano=2025):
    c.setFillColor(white)
    c.rect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, fill=1, stroke=0)

    title = "Agenda Devocional"
    subtitle = f"{mes} {ano}"
    tagline = "Mensagens e Versículos para cada dia"
    verse = "\"Lâmpada para os meus pés é tua palavra e luz, para o meu caminho.\""
    ref = "Salmos 119:105"

    # Título
    c.setFillColor(BLUE_DARK)
    c.setFont("Helvetica-Bold", 36)
    tw = pdfmetrics.stringWidth(title, "Helvetica-Bold", 36)
    c.drawString((PAGE_WIDTH - tw) / 2, PAGE_HEIGHT * 0.65, title)

    # Subtítulo
    c.setFillColor(GRAY_TEXT)
    c.setFont("Helvetica", 24)
    tw = pdfmetrics.stringWidth(subtitle, "Helvetica", 24)
    c.drawString((PAGE_WIDTH - tw) / 2, PAGE_HEIGHT * 0.58, subtitle)

    # Tagline
    c.setFont("Helvetica-Oblique", 14)
    tw = pdfmetrics.stringWidth(tagline, "Helvetica-Oblique", 14)
    c.drawString((PAGE_WIDTH - tw) / 2, PAGE_HEIGHT * 0.52, tagline)

    # Caixa do versículo
    box_width = PAGE_WIDTH * 0.6
    box_x = (PAGE_WIDTH - box_width) / 2
    box_y = PAGE_HEIGHT * 0.32
    box_h = 90

    c.setFillColor(LIGHT_BLUE)
    c.roundRect(box_x, box_y, box_width, box_h, 10, fill=1, stroke=0)

    text_x = box_x + 16
    text_y = box_y + box_h - 20

    text_y = draw_paragraph(
        c,
        verse,
        text_x,
        text_y,
        box_width - 32,
        font_name="Helvetica-Oblique",
        font_size=12,
        color=GRAY_TEXT,
    )
    c.setFont("Helvetica", 10)
    c.setFillColor(BLUE_DARK)
    c.drawString(text_x, text_y - 8, ref)


# -----------------------------
#  Bloco de um dia (2 por página)
# -----------------------------

def desenhar_dia_bloco(c, dia_info, x, y_top, width, height):
    """
    Desenha UM dia dentro de um bloco retangular:
    - x, y_top: canto superior esquerdo
    - width, height: largura/altura disponíveis
    """

    y_bottom = y_top - height

    # Cabeçalho do bloco
    header_h = 45
    header_bottom = y_top - header_h
    header_top = y_top

    c.setFillColor(BLUE)
    c.roundRect(x, header_bottom, width, header_h, 6, fill=1, stroke=0)

    padding_x = 12

    # Dia da semana + data
    c.setFillColor(white)
    c.setFont("Helvetica", 9)
    c.drawString(x + padding_x, header_top - 16, dia_info["dia_semana"].upper())

    c.setFont("Helvetica-Bold", 16)
    c.drawString(x + padding_x, header_top - 32, dia_info["data"])

    # Círculo com número do dia
    cx = x + width - 30
    cy = header_bottom + header_h / 2
    c.setFillColor(white)
    c.circle(cx, cy, 20, fill=1, stroke=0)

    c.setFillColor(BLUE_DARK)
    c.setFont("Helvetica-Bold", 16)
    tw = pdfmetrics.stringWidth(dia_info["dia"], "Helvetica-Bold", 16)
    c.drawString(cx - tw / 2, cy - 6, dia_info["dia"])

    # Conteúdo do bloco
    content_y = header_bottom - 18
    max_text_width = width

    # MENSAGEM DO DIA
    c.setFont("Helvetica-Bold", 9)
    c.setFillColor(BLUE_DARK)
    c.drawString(x, content_y, "MENSAGEM DO DIA")
    content_y -= 14

    content_y = draw_paragraph(
        c,
        dia_info["mensagem"],
        x,
        content_y,
        max_text_width,
        font_name="Helvetica",
        font_size=11,
        color=GRAY_TEXT,
    )
    content_y -= 14

    # Caixa do versículo
    box_height = 95
    box_y = content_y - box_height + 10
    if box_y < y_bottom + 40:  # evita colar demais no rodapé do bloco
        box_y = y_bottom + 40

    c.setFillColor(LIGHT_BLUE)
    c.roundRect(x, box_y, width, box_height, 6, fill=1, stroke=0)

    inner_x = x + 10
    inner_y = box_y + box_height - 16

    c.setFont("Helvetica-Bold", 9)
    c.setFillColor(BLUE_DARK)
    c.drawString(inner_x, inner_y, "VERSÍCULO")
    inner_y -= 14

    inner_y = draw_paragraph(
        c,
        f"\"{dia_info['versiculo']}\"",
        inner_x,
        inner_y,
        width - 20,
        font_name="Helvetica-Oblique",
        font_size=10,
        color=GRAY_TEXT,
    )
    inner_y -= 6

    c.setFont("Helvetica-Bold", 9)
    c.setFillColor(BLUE_DARK)
    c.drawString(inner_x, inner_y, dia_info["referencia"])

    # Minhas anotações (menos linhas para caber bem)
    content_y = box_y - 18
    if content_y > y_bottom + 40:
        c.setFont("Helvetica-Bold", 9)
        c.setFillColor(GRAY_TEXT)
        c.drawString(x, content_y, "MINHAS ANOTAÇÕES")
        content_y -= 10

        c.setStrokeColor(LIGHT_GRAY)
        lines = 5  # 5 linhas de anotação
        for _ in range(lines):
            content_y -= 14
            if content_y < y_bottom + 20:
                break
            c.line(x, content_y, x + width, content_y)


# -----------------------------
#  Rodapé da página
# -----------------------------

def desenhar_rodape(c, page_number, ano=2025):
    footer_y = 25
    c.setFont("Helvetica", 8)
    c.setFillColor(GRAY_TEXT)

    left_text = f"Agenda Devocional {ano}"
    c.drawString(MARGIN_X, footer_y, left_text)

    right_text = f"Página {page_number}"
    tw = pdfmetrics.stringWidth(right_text, "Helvetica", 8)
    c.drawString(PAGE_WIDTH - MARGIN_X - tw, footer_y, right_text)


# -----------------------------
#  Ler JSON
# -----------------------------

def carregar_dias_json(path_json):
    p = Path(path_json)
    data = json.loads(p.read_text(encoding="utf-8"))
    return data.get("dias", []), data.get("mes", "Dezembro"), data.get("ano", 2025)


# -----------------------------
#  Função principal
# -----------------------------

def gerar_agenda_devocional(
    json_path="devocional_dezembro_2025.json",
    output_path="Agenda_Devocional_Dezembro_2025.pdf",
):
    dias, mes, ano = carregar_dias_json(json_path)
    c = canvas.Canvas(output_path, pagesize=A4)

    # Capa
    desenhar_capa(c, mes=mes, ano=ano)
    c.showPage()

    # Configuração dos blocos (2 por página)
    block_width = PAGE_WIDTH - 2 * MARGIN_X
    available_height = PAGE_HEIGHT - 2 * MARGIN_TOP - 20  # 20 de espaço entre blocos
    block_height = available_height / 2

    page_num = 2
    i = 0
    while i < len(dias):
        # Bloco superior
        y_top_first = PAGE_HEIGHT - MARGIN_TOP
        desenhar_dia_bloco(
            c,
            dias[i],
            x=MARGIN_X,
            y_top=y_top_first,
            width=block_width,
            height=block_height,
        )

        # Bloco inferior (se existir próximo dia)
        if i + 1 < len(dias):
            y_top_second = y_top_first - block_height - 20  # gap de 20
            desenhar_dia_bloco(
                c,
                dias[i + 1],
                x=MARGIN_X,
                y_top=y_top_second,
                width=block_width,
                height=block_height,
            )

        # Rodapé da página
        desenhar_rodape(c, page_number=page_num, ano=ano)

        c.showPage()
        page_num += 1
        i += 2  # avança dois dias por página

    c.save()
    print(f"PDF gerado em: {output_path}")


if __name__ == "__main__":
    gerar_agenda_devocional()
