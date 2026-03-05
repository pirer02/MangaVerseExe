#!/usr/bin/python3
# -*- coding: utf-8 -*-

import os
import re
import sys
import json
import shutil
import time
import logging
import cloudscraper
from bs4 import BeautifulSoup

# --- CONFIGURACIÓN ---
LIBRARY_PATH = "/library"
CHECK_INTERVAL_SECONDS = 3600  # 1 hora
MIN_FILE_SIZE = 5120 

# Logging setup
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[logging.StreamHandler(), logging.FileHandler("updater.log")]
)
logger = logging.getLogger("MangaUpdater")

SCRAPER = cloudscraper.create_scraper()

# --- LÓGICA DE FORMATO ---

def clean_folder_name(name):
    return re.sub(r'[^a-zA-Z0-9_\-\.]', '', name.replace(" ", "_").replace("-", "_"))

def calculate_padding(all_nums):
    """Calcula cuántos ceros poner basándose en el capítulo más alto encontrado online"""
    if not all_nums: return 3
    max_val = max(all_nums)
    digits = len(str(int(max_val)))
    return max(2, digits)

def format_chapter_name(chapter_num, all_chapter_nums, padding):
    """
    Formatea usando el padding y la lógica de decimales -> letras.
    CORREGIDO: Sin punto antes de la letra.
    Ejemplo: 100.0 -> "100"
    Ejemplo: 100.5 -> "100A" (NO "100.A")
    """
    val = float(chapter_num)
    int_val = int(val)
    
    # Padding dinámico (Ej: 001, 099, 100)
    base = f"{int_val:0{padding}d}"
    
    # Caso 1: Es un entero exacto (Ej: 100.0 -> 100)
    if val.is_integer():
        return base
    
    # Caso 2: Tiene decimales, convertir a letra
    # Buscamos todos los capítulos que comparten el mismo número entero (ej: 100.1, 100.5)
    decimals = sorted([float(c) for c in all_chapter_nums if int(float(c)) == int_val and not float(c).is_integer()])
    try:
        # Asigna A, B, C... SIN EL PUNTO INTERMEDIO
        # chr(65) es 'A', chr(66) es 'B', etc.
        return f"{base}{chr(65 + decimals.index(val))}"
    except:
        # Fallback por si acaso
        return f"{base}dec"

def create_cbz(source, output_path_no_ext):
    try:
        shutil.make_archive(output_path_no_ext, 'zip', source)
        cbz = output_path_no_ext + '.cbz'
        if os.path.exists(cbz): os.remove(cbz)
        os.rename(output_path_no_ext + '.zip', cbz)
        shutil.rmtree(source)
        return True
    except Exception as e:
        logger.error(f"Error creando CBZ: {e}")
        return False

def validate_image(path):
    if not os.path.exists(path): return False
    if os.path.getsize(path) < MIN_FILE_SIZE:
        try: os.remove(path)
        except: pass
        return False
    return True

# --- PROVEEDORES ---

class InMangaProvider:
    NAME = "InManga"
    @staticmethod
    def search(query):
        try:
            url = "https://inmanga.com/manga/getMangasConsultResult"
            res = SCRAPER.post(url, data={'filter[queryString]': query, 'filter[take]': 5, 'filter[sortby]': 1}, headers={'X-Requested-With': 'XMLHttpRequest'}, timeout=20)
            soup = BeautifulSoup(res.content, 'html.parser')
            return [{'provider': 'InManga', 'title': a.find('h4').text.strip(), 'id': a['href'].split('/')[-1], 'url': f"https://inmanga.com{a['href']}"} 
                    for a in soup.find_all('a', href=True) if '/ver/manga/' in a['href']]
        except: return []

    @staticmethod
    def get_chapters(manga_data):
        try:
            r = SCRAPER.get(f"https://inmanga.com/chapter/getall?mangaIdentification={manga_data['id']}", timeout=20)
            raw = json.loads(json.loads(r.content)['data'])['result']
            return [{'number': float(c['Number']), 'id': c['Identification'], 'source_data': manga_data, 'cls': InMangaProvider} for c in raw]
        except: return []

    @staticmethod
    def get_images(chap):
        try:
            url = f"https://inmanga.com/chapter/chapterIndexControls?identification={chap['id']}"
            soup = BeautifulSoup(SCRAPER.get(url, timeout=20).content, 'html.parser')
            pages = soup.select('#PageList option')
            if not pages: return []
            return [f"https://cdn1.intomanga.com/i/m/{chap['source_data']['id']}/c/{chap['id'].lower()}/o/{o['value']}.jpg" for o in pages]
        except: return []

class AnzMangaProvider:
    NAME = "AnzManga"
    @staticmethod
    def search(query):
        slug = query.lower().strip().replace(" ", "-")
        url = f"https://www.anzmanga25.com/manga/{slug}"
        try:
            if SCRAPER.get(url, timeout=20).status_code == 200:
                return [{'provider': 'AnzManga', 'title': query.title(), 'url': url, 'id': url}]
        except: pass
        return []

    @staticmethod
    def get_chapters(manga_data):
        try:
            soup = BeautifulSoup(SCRAPER.get(manga_data['url'], timeout=20).content, 'html.parser')
            chaps = []
            for a in soup.select('li.wp-manga-chapter a, ul.chapters a'):
                if num := (re.search(r'(\d+(\.\d+)?)', a.get_text()) or re.search(r'capitulo-(\d+(\.\d+)?)', a['href'])):
                    chaps.append({'number': float(num.group(1)), 'url': a['href'], 'source_data': manga_data, 'cls': AnzMangaProvider})
            return chaps
        except: return []

    @staticmethod
    def get_images(chap):
        # --- LÓGICA DE PAGINACIÓN IMPLEMENTADA AQUÍ ---
        images_urls = []
        try:
            base_url = chap['url'].rstrip('/')
            if base_url.endswith('/1'):
                base_url = base_url[:-2]
                
            page_num = 1
            downloaded_urls = set()
            
            while True:
                page_url = f"{base_url}/{page_num}"
                res_page = SCRAPER.get(page_url, timeout=20)
                
                if res_page.status_code != 200:
                    break
                
                if page_num > 1 and page_url.rstrip('/') not in res_page.url:
                    break
                    
                soup_page = BeautifulSoup(res_page.content, 'html.parser')
                
                strict_selectors = [
                    '#chapter_imgs img', '.reading-content img', '#readerarea img', 
                    '.page-break img', '#image-container img', '#manga-page', 
                    'img.wp-manga-chapter-img', 'img#image', '.chapter-image img'
                ]
                
                img_tags = []
                for selector in strict_selectors:
                    elements = soup_page.select(selector)
                    if elements:
                        img_tags = elements
                        break
                
                if not img_tags:
                    img_tags = soup_page.find_all('img')

                blacklist = [
                    'logo', 'banner', 'facebook', 'twitter', 'loading', 'avatar',
                    'sidebar', 'icon', 'promo', 'footer', 'wp-smiley', 'discord', 
                    'telegram', 'header', 'pixel.gif'
                ]
                
                valid_img_url = None
                for img in img_tags:
                    url = img.get('data-src') or img.get('data-lazy-src') or img.get('src')
                    if not url: continue
                    
                    url = url.strip()
                    if url.startswith('//'): url = 'https:' + url
                    if not url.startswith('http'): continue
                    
                    url_lower = url.lower()
                    if any(x in url_lower for x in blacklist): 
                        continue
                    
                    if url not in downloaded_urls:
                        valid_img_url = url
                        break

                if not valid_img_url:
                    break
                    
                downloaded_urls.add(valid_img_url)
                images_urls.append(valid_img_url)
                page_num += 1
                
        except Exception as e:
            logger.error(f"Error extrayendo imágenes de AnzManga: {e}")
            
        return images_urls

# --- MOTOR PRINCIPAL ---

def get_last_local_chapter(manga_path):
    """Devuelve el número (float) del capítulo más alto que ya tienes."""
    if not os.path.exists(manga_path): return 0.0
    
    files = [f for f in os.listdir(manga_path) if f.endswith('.cbz')]
    if not files: return 0.0
    
    max_num = 0.0
    for f in files:
        match = re.match(r'^(\d+)', f)
        if match:
            try:
                val = float(match.group(1))
                if val > max_num: max_num = val
            except: pass
            
    return max_num

def process_manga_updater(folder_name):
    clean_name = folder_name.replace("_", " ")
    manga_path = os.path.join(LIBRARY_PATH, folder_name)
    
    logger.info(f"Comprobando: {clean_name}")

    # --- MODIFICACIÓN: Lógica híbrida estilo 'total.py' ---
    # 1. Búsqueda combinada
    results = InMangaProvider.search(clean_name) + AnzMangaProvider.search(clean_name)
    
    # 2. Filtrado estricto (coincidencia exacta)
    valid_targets = [r for r in results if r['title'].lower() == clean_name.lower()]
    
    if not valid_targets:
        logger.warning(f" -> No encontrado online (Coincidencia exacta requerida).")
        return

    # 3. Mapeo de capítulos de múltiples fuentes
    chapter_map = {}
    for target in valid_targets:
        Provider = InMangaProvider if target['provider'] == "InManga" else AnzMangaProvider
        try:
            chaps = Provider.get_chapters(target)
            for c in chaps:
                num = c['number']
                if num not in chapter_map: chapter_map[num] = []
                # Priorizar InManga insertando al principio (como en total.py)
                if target['provider'] == "InManga":
                    chapter_map[num].insert(0, c)
                else:
                    chapter_map[num].append(c)
        except:
            continue

    if not chapter_map:
        return

    all_chapter_nums = sorted(chapter_map.keys())
    padding = calculate_padding(all_chapter_nums)

    last_local = get_last_local_chapter(manga_path)
    
    # Filtrar nuevos (solo números mayores al local)
    new_chapter_nums = [n for n in all_chapter_nums if n > last_local]

    # --- PECULIARIDAD: REGLA DE ONE PIECE ---
    is_one_piece = (clean_name.lower() == "one piece")
    latest_chapter = all_chapter_nums[-1] if all_chapter_nums else None

    if is_one_piece and latest_chapter is not None:
        if latest_chapter not in new_chapter_nums:
            new_chapter_nums.append(latest_chapter)
            logger.info(f" -> [One Piece] Detectado último cap: {latest_chapter}. Se forzará la actualización.")

    if not new_chapter_nums:
        logger.info(f" -> Al día (Último: {last_local})")
        return

    logger.info(f" -> ¡Encontrados {len(new_chapter_nums)} capítulos para procesar! (Padding: {padding})")

    for num in new_chapter_nums:
        fmt_name = format_chapter_name(num, all_chapter_nums, padding)
        final_cbz_path_no_ext = os.path.join(manga_path, fmt_name)
        
        # Verificar si ya existe
        if os.path.exists(final_cbz_path_no_ext + ".cbz"):
            # Excepción: Si es One Piece y es el último capítulo, lo borramos para bajarlo de nuevo
            if is_one_piece and num == latest_chapter:
                logger.info(f"    -> [One Piece] Eliminando versión anterior del cap {fmt_name} para actualizarla...")
                try:
                    os.remove(final_cbz_path_no_ext + ".cbz")
                except: pass
            else:
                continue

        candidates = chapter_map[num]
        success = False

        # --- Intento de descarga con fallback ---
        for candidate in candidates:
            Provider = candidate['cls']
            temp_dir = os.path.join(manga_path, f"temp_{fmt_name}")
            
            logger.info(f"    -> Descargando {fmt_name} desde {Provider.NAME}...")
            
            images = Provider.get_images(candidate)
            if not images:
                continue # Probar siguiente candidato

            os.makedirs(temp_dir, exist_ok=True)
            valid_count = 0
            
            # Referer dinámico según el target original del candidato
            referer_url = candidate['source_data']['url']

            for i, url in enumerate(images, 1):
                try:
                    r = SCRAPER.get(url, headers={'Referer': referer_url}, timeout=10)
                    if r.status_code == 200:
                        fpath = os.path.join(temp_dir, f"{i:03d}.jpg")
                        with open(fpath, 'wb') as f: f.write(r.content)
                        if validate_image(fpath): valid_count += 1
                except: pass
            
            if valid_count > 0:
                if create_cbz(temp_dir, final_cbz_path_no_ext):
                    logger.info(f"       [OK] Guardado como {fmt_name}.cbz")
                    success = True
                    break # Éxito, salir del bucle de candidatos
                else:
                    logger.error("       [ERROR] Fallo al crear CBZ")
            else:
                shutil.rmtree(temp_dir, ignore_errors=True)
            
            time.sleep(1) # Pequeña pausa entre intentos de proveedor

        if not success:
             logger.warning(f"    -> Fallo al descargar {fmt_name} de todas las fuentes disponibles.")

        time.sleep(2)

if __name__ == "__main__":
    logger.info("=== INICIANDO MANGA UPDATER v2.1 (Multi-source) ===")
    while True:
        try:
            if not os.path.exists(LIBRARY_PATH):
                logger.error(f"No existe {LIBRARY_PATH}")
                time.sleep(60)
                continue

            folders = sorted([f for f in os.listdir(LIBRARY_PATH) if os.path.isdir(os.path.join(LIBRARY_PATH, f))])
            
            for folder in folders:
                process_manga_updater(folder)
            
            logger.info(f"--- Ciclo terminado. Durmiendo {CHECK_INTERVAL_SECONDS/60} mins ---")
            time.sleep(CHECK_INTERVAL_SECONDS)
            
        except Exception as e:
            logger.critical(f"Error global: {e}")
            time.sleep(60)