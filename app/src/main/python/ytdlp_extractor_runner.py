import hashlib
import json

from engine_runtime import (
    DEFAULT_USER_AGENT,
    YTDLP_AVAILABLE,
    cleanup_temp_file,
    create_netscape_cookie_file,
    parse_options,
    yt_dlp,
)


def _format_duration_string(seconds_val):
    if seconds_val is None:
        return None
    try:
        total = int(float(seconds_val))
    except (TypeError, ValueError):
        return None
    if total <= 0:
        return None
    hours, rem = divmod(total, 3600)
    minutes, secs = divmod(rem, 60)
    if hours > 0:
        return f"{hours}:{minutes:02d}:{secs:02d}"
    return f"{minutes:02d}:{secs:02d}"


def _build_compact_format(f):
    ext_val = (f.get("ext") or "").lower() or "mp4"
    vcodec = f.get("vcodec")
    acodec = f.get("acodec")
    has_video = vcodec not in (None, "none")
    has_audio = acodec not in (None, "none")
    is_audio_only = (not has_video) and (
        has_audio or ext_val in ("mp3", "m4a", "opus", "aac", "ogg", "flac", "wav")
    )
    return {
        "format_id": str(f.get("format_id") or ""),
        "ext": ext_val,
        "width": f.get("width"),
        "height": f.get("height"),
        "fps": f.get("fps"),
        "vcodec": vcodec,
        "acodec": acodec,
        "tbr": f.get("tbr"),
        "abr": f.get("abr"),
        "filesize": f.get("filesize") or f.get("filesize_approx"),
        "format_note": f.get("format_note"),
        "protocol": f.get("protocol"),
        "has_video": has_video,
        "has_audio": has_audio,
        "is_audio_only": is_audio_only,
        "url": f.get("url"),
    }


def _select_best_direct_format(entry):
    """
    Resolves the best direct playable media URL and preserves diverse quality,
    codec, and audio-only formats for user selection.
    """
    formats = entry.get("formats") or []
    requested = entry.get("requested_formats") or []

    entry_url = entry.get("url")
    entry_ext = entry.get("ext") or ""

    http_formats = [
        f for f in formats
        if isinstance(f, dict)
        and isinstance(f.get("url"), str)
        and f["url"].startswith(("http://", "https://"))
        and (f.get("ext") or "").lower() != "mhtml"
    ]

    chosen_fmt = None
    muxed = [
        f for f in http_formats
        if f.get("vcodec") not in (None, "none")
        and f.get("acodec") not in (None, "none")
    ]
    if muxed:
        chosen_fmt = max(
            muxed,
            key=lambda f: (
                int(f.get("height") or 0),
                int(f.get("width") or 0),
                float(f.get("tbr") or 0),
                int(f.get("filesize") or f.get("filesize_approx") or 0),
            ),
        )
    elif entry_url and entry_url.startswith(("http://", "https://")) and entry_ext.lower() != "mhtml":
        chosen_fmt = entry
    elif http_formats:
        chosen_fmt = max(
            http_formats,
            key=lambda f: (
                1 if f.get("vcodec") not in (None, "none") else 0,
                int(f.get("height") or 0),
                float(f.get("tbr") or 0),
            ),
        )
    elif requested and isinstance(requested[0], dict) and requested[0].get("url"):
        chosen_fmt = requested[0]

    direct_url = None
    if chosen_fmt and isinstance(chosen_fmt.get("url"), str):
        direct_url = chosen_fmt["url"]
    elif entry_url and isinstance(entry_url, str) and entry_url.startswith(("http://", "https://")):
        direct_url = entry_url

    # Preserve muxed video+audio, separate high-res video, and audio-only tracks without truncation dropping audio
    muxed_compact = []
    video_only_compact = []
    audio_only_compact = []
    seen_format_ids = set()

    for f in http_formats:
        compact = _build_compact_format(f)
        fid = compact["format_id"] or compact["url"]
        if fid in seen_format_ids:
            continue
        seen_format_ids.add(fid)
        if compact["is_audio_only"]:
            audio_only_compact.append(compact)
        elif compact["has_video"] and compact["has_audio"]:
            muxed_compact.append(compact)
        elif compact["has_video"]:
            video_only_compact.append(compact)

    muxed_compact.sort(
        key=lambda c: (int(c.get("height") or 0), float(c.get("tbr") or 0)),
        reverse=True,
    )
    video_only_compact.sort(
        key=lambda c: (int(c.get("height") or 0), float(c.get("tbr") or 0)),
        reverse=True,
    )
    audio_only_compact.sort(
        key=lambda c: (float(c.get("abr") or c.get("tbr") or 0), int(c.get("filesize") or 0)),
        reverse=True,
    )

    compact_formats = muxed_compact[:12] + video_only_compact[:12] + audio_only_compact[:6]
    return direct_url, chosen_fmt or {}, compact_formats


def _build_ytdlp_item(entry, clean_url, direct_url, chosen_fmt, compact_formats, index):
    v_title = entry.get("title") or entry.get("id") or f"media_{index + 1}"
    v_ext = (chosen_fmt.get("ext") or entry.get("ext") or "mp4").lower()
    if v_ext == "mhtml":
        v_ext = "mp4"
    item_title = f"{v_title}.{v_ext}" if not v_title.endswith(f".{v_ext}") else v_title

    width = chosen_fmt.get("width") or entry.get("width")
    height = chosen_fmt.get("height") or entry.get("height")
    resolution_hint = f"{width}x{height}" if width and height else None
    thumb = entry.get("thumbnail")
    filesize = (
        chosen_fmt.get("filesize")
        or chosen_fmt.get("filesize_approx")
        or entry.get("filesize")
        or entry.get("filesize_approx")
    )

    raw_duration = entry.get("duration") or chosen_fmt.get("duration")
    try:
        duration_sec = int(float(raw_duration)) if raw_duration and float(raw_duration) > 0 else None
    except (TypeError, ValueError):
        duration_sec = None

    uploader = (
        entry.get("uploader")
        or entry.get("channel")
        or entry.get("creator")
        or entry.get("artist")
        or entry.get("uploader_id")
    )
    upload_date = entry.get("upload_date") or entry.get("release_date")
    media_id = entry.get("id") or entry.get("display_id")

    vcodec = chosen_fmt.get("vcodec") if chosen_fmt else entry.get("vcodec")
    is_audio_only = vcodec == "none" or v_ext in ["mp3", "m4a", "opus", "aac", "ogg", "flac", "wav"]
    lower_direct = direct_url.lower()
    lower_proto = (chosen_fmt.get("protocol") or "").lower()
    is_stream = (
        (".m3u8" in lower_direct)
        or (".mpd" in lower_direct)
        or ("m3u8" in lower_proto)
        or ("dash" in lower_proto)
    )
    category = "AUDIO" if is_audio_only else ("STREAMS" if is_stream else "VIDEOS")
    mime_type = (
        f"audio/{v_ext}"
        if is_audio_only
        else ("application/x-mpegURL" if ".m3u8" in lower_direct else ("application/dash+xml" if ".mpd" in lower_direct else f"video/{v_ext}"))
    )

    http_headers = {}
    if isinstance(entry.get("http_headers"), dict):
        http_headers.update({str(k): str(v) for k, v in entry["http_headers"].items() if v is not None})
    if isinstance(chosen_fmt.get("http_headers"), dict):
        http_headers.update({str(k): str(v) for k, v in chosen_fmt["http_headers"].items() if v is not None})

    webpage_url = entry.get("webpage_url") or clean_url
    url_hash = hashlib.sha256(direct_url.encode("utf-8")).hexdigest()[:12]
    item = {
        "id": f"ytdlp_{index}_{url_hash}",
        "url": direct_url,
        "webpageUrl": webpage_url,
        "title": item_title,
        "category": category,
        "format": v_ext.upper(),
        "mimeType": mime_type,
        "isVideo": not is_audio_only,
        "isAudio": is_audio_only,
        "isStream": is_stream,
        "thumbnailUrl": thumb,
        "resolutionHint": resolution_hint,
        "sizeBytes": filesize,
        "durationSeconds": duration_sec,
        "durationText": _format_duration_string(duration_sec),
        "uploader": str(uploader).strip() if uploader else None,
        "uploadDate": str(upload_date).strip() if upload_date else None,
        "mediaId": str(media_id).strip() if media_id else None,
        "source": "yt-dlp",
        "httpHeaders": http_headers,
        "formats": compact_formats[:30],
    }
    return item, v_title


def extract_yt_dlp(url, options_json="{}"):
    """
    Extracts media items using yt-dlp exclusively.
    Propagates session cookies, preserves available formats, and emits direct media URLs
    alongside webpage_url, duration, uploader, upload_date, and id metadata.
    """
    clean_url = url.strip() if url else ""
    if not clean_url or not YTDLP_AVAILABLE:
        return json.dumps({
            "success": False,
            "items": [],
            "error": "yt-dlp not available or empty URL",
        })

    opts = parse_options(options_json)
    user_agent = opts.get("user_agent") or DEFAULT_USER_AGENT
    cookies = opts.get("cookies")
    timeout = int(opts.get("timeout") or 20)
    max_items = int(opts.get("max_items") or 30)

    items = []
    page_title = ""
    seen_urls = set()
    cookie_file = create_netscape_cookie_file(cookies, clean_url)

    try:
        http_headers = {"User-Agent": user_agent}
        if cookies and isinstance(cookies, str) and cookies.strip():
            http_headers["Cookie"] = cookies.strip()

        ydl_opts = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "format": "b[vcodec!=none][acodec!=none]/best[vcodec!=none][acodec!=none]/best",
            "socket_timeout": timeout,
            "playlistend": max_items,
            "http_headers": http_headers,
        }
        if cookie_file:
            ydl_opts["cookiefile"] = cookie_file

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(clean_url, download=False)
            if info:
                entries = info.get("entries")
                if entries is not None:
                    entries_list = []
                    for e in entries:
                        if e:
                            entries_list.append(e)
                        if len(entries_list) >= max_items:
                            break
                else:
                    entries_list = [info]

                for entry in entries_list:
                    if not entry or not isinstance(entry, dict):
                        continue

                    direct_url, chosen_fmt, compact_formats = _select_best_direct_format(entry)
                    if not direct_url:
                        direct_url = entry.get("webpage_url") or clean_url
                    if not direct_url or direct_url in seen_urls:
                        continue

                    seen_urls.add(direct_url)
                    item, v_title = _build_ytdlp_item(
                        entry, clean_url, direct_url, chosen_fmt, compact_formats, len(items)
                    )
                    if not page_title:
                        page_title = info.get("title") or v_title
                    items.append(item)
    except Exception as e:
        return json.dumps({
            "success": False,
            "error": f"{e.__class__.__name__}: {str(e)}",
            "items": [],
        })
    finally:
        cleanup_temp_file(cookie_file)

    return json.dumps({
        "success": len(items) > 0,
        "engine": "yt-dlp",
        "pageTitle": str(page_title) if page_title else "",
        "itemsCount": len(items),
        "items": items,
    })
