import json
import sys
import threading

DEFAULT_USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
)

_gdl_lock = threading.Lock()

try:
    import gallery_dl
    from gallery_dl import (
        extractor as gdl_extractor,
        job as gdl_job,
        config as gdl_config,
    )
    GALLERY_DL_AVAILABLE = True
except Exception:
    gallery_dl = None
    gdl_extractor = None
    gdl_job = None
    gdl_config = None
    GALLERY_DL_AVAILABLE = False

try:
    import yt_dlp
    YTDLP_AVAILABLE = True
except Exception:
    yt_dlp = None
    YTDLP_AVAILABLE = False

POPULAR_EXTRACTORS = [
    {"name": "Reddit", "domains": ["reddit.com", "redd.it", "i.redd.it", "v.redd.it"], "icon": "reddit"},
    {"name": "Imgur", "domains": ["imgur.com", "i.imgur.com"], "icon": "image"},
    {"name": "Twitter / X", "domains": ["twitter.com", "x.com", "pbs.twimg.com"], "icon": "tag"},
    {"name": "Pinterest", "domains": ["pinterest.com", "i.pinimg.com"], "icon": "pin"},
    {"name": "ArtStation", "domains": ["artstation.com"], "icon": "brush"},
    {"name": "DeviantArt", "domains": ["deviantart.com"], "icon": "palette"},
    {"name": "Flickr", "domains": ["flickr.com"], "icon": "camera"},
]

import os
import tempfile
from urllib.parse import urlparse

def parse_options(options_json):
    """Safely parses JSON extractor options."""
    if not options_json:
        return {}
    try:
        parsed = json.loads(options_json)
        return parsed if isinstance(parsed, dict) else {}
    except Exception:
        return {}


def create_netscape_cookie_file(cookies_str, url):
    """
    Converts a raw HTTP Cookie header string ('k1=v1; k2=v2') into a temporary
    Netscape cookies.txt file usable by both yt-dlp and gallery-dl.
    Returns the temp file path or None if no valid cookie pairs exist.
    """
    if not cookies_str or not isinstance(cookies_str, str):
        return None
    trimmed = cookies_str.strip()
    if not trimmed:
        return None

    # If caller already passed a valid existing file path, reuse it directly (caller won't delete it).
    if os.path.isfile(trimmed):
        return None

    parsed_url = urlparse(url or "")
    host = (parsed_url.hostname or "").strip().lower()
    if not host:
        return None

    is_https = parsed_url.scheme.lower() == "https"
    secure_flag = "TRUE" if is_https else "FALSE"
    # Use leading dot domain to match subdomains (e.g., .reddit.com, .x.com)
    parts = host.split(".")
    base_domain = f".{'.'.join(parts[-2:])}" if len(parts) >= 2 else host

    lines = ["# Netscape HTTP Cookie File\n"]
    pair_count = 0
    for segment in trimmed.split(";"):
        part = segment.strip()
        if "=" not in part:
            continue
        name, value = part.split("=", 1)
        name = name.strip()
        value = value.strip()
        if not name:
            continue
        # Write entry for exact host and apex domain
        lines.append(f"{host}\tFALSE\t/\t{secure_flag}\t0\t{name}\t{value}\n")
        if base_domain != host:
            lines.append(f"{base_domain}\tTRUE\t/\t{secure_flag}\t0\t{name}\t{value}\n")
        pair_count += 1

    if pair_count == 0:
        return None

    fd, temp_path = tempfile.mkstemp(prefix="emre_cookies_", suffix=".txt")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.writelines(lines)
        return temp_path
    except Exception:
        try:
            os.remove(temp_path)
        except Exception:
            pass
        return None


def cleanup_temp_file(path):
    """Safely removes a temporary file if it exists."""
    if path:
        try:
            if os.path.exists(path):
                os.remove(path)
        except Exception:
            pass


def build_http_headers(user_agent=None, referer=None, cookies=None, headers_json=None):
    """Constructs merged HTTP headers for extraction or download requests."""
    headers = {"User-Agent": user_agent or DEFAULT_USER_AGENT}
    if referer:
        headers["Referer"] = referer
    if cookies:
        headers["Cookie"] = cookies
    if headers_json:
        try:
            parsed = json.loads(headers_json)
            if isinstance(parsed, dict):
                headers.update(parsed)
        except Exception:
            pass
    return headers


def get_engine_status():
    """Returns runtime status and version information synchronized with GalleryDlEngine."""
    gdl_ver = getattr(gallery_dl, "__version__", "1.32.19") if GALLERY_DL_AVAILABLE else "N/A"
    ytdlp_ver = getattr(yt_dlp, "__version__", "2026.10.21") if YTDLP_AVAILABLE else "N/A"
    available = GALLERY_DL_AVAILABLE or YTDLP_AVAILABLE
    engine_name = f"gallery-dl {gdl_ver} / yt-dlp {ytdlp_ver}" if available else "Python Engine Unavailable"

    return json.dumps({
        "available": available,
        "version": gdl_ver,
        "engine": engine_name,
        "gallery_dl_available": GALLERY_DL_AVAILABLE,
        "gallery_dl_version": gdl_ver,
        "yt_dlp_available": YTDLP_AVAILABLE,
        "yt_dlp_version": ytdlp_ver,
        "python_version": sys.version.split()[0],
        "supported_categories_count": 287 if GALLERY_DL_AVAILABLE else 0,
        "popular_extractors": POPULAR_EXTRACTORS,
    })


def check_ytdlp_support(url):
    """Checks if a URL matches any non-generic yt-dlp extractor or direct stream manifest."""
    if not url or not YTDLP_AVAILABLE:
        return json.dumps({"supported": False, "extractor": ""})

    clean_url = url.strip()
    lower = clean_url.lower()
    clean_path = lower.split("?")[0].split("#")[0]
    if clean_path.endswith(".m3u8") or clean_path.endswith(".mpd"):
        return json.dumps({"supported": True, "extractor": "StreamManifest"})

    try:
        from yt_dlp.extractor import gen_extractors
        for ie in gen_extractors():
            ie_name = getattr(ie, "IE_NAME", "") or ""
            if ie_name.lower() == "generic":
                continue
            try:
                if ie.suitable(clean_url):
                    return json.dumps({"supported": True, "extractor": ie_name})
            except Exception:
                continue
    except Exception:
        pass

    return json.dumps({"supported": False, "extractor": ""})


def check_support(url):
    """Checks if a URL is handled by gallery-dl or yt-dlp extractors."""
    if not url:
        return json.dumps({"supported": False, "category": "", "extractor": ""})

    clean_url = url.strip()

    if GALLERY_DL_AVAILABLE:
        try:
            ext = gdl_extractor.find(clean_url)
            if ext is not None:
                cat = getattr(ext, "category", "") or ext.__class__.__name__.lower().replace("extractor", "")
                subcat = getattr(ext, "subcategory", "") or ""
                name = ext.__class__.__name__
                return json.dumps({
                    "supported": True,
                    "category": cat,
                    "subcategory": subcat,
                    "extractor": name,
                    "engine": "gallery-dl",
                })
        except Exception:
            pass

    if YTDLP_AVAILABLE:
        try:
            yt_res = json.loads(check_ytdlp_support(clean_url))
            if yt_res.get("supported"):
                return json.dumps({
                    "supported": True,
                    "category": "video",
                    "subcategory": "stream",
                    "extractor": yt_res.get("extractor") or "YoutubeDL",
                    "engine": "yt-dlp",
                })
        except Exception:
            pass

    return json.dumps({"supported": False, "category": "", "extractor": ""})
