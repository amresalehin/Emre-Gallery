package com.amresalehin.emregallery.data

/**
 * Runtime WebView JavaScript snippets for dynamic DOM observation and adaptive scrolling.
 */
internal object BrowserRuntimeScripts {

    /**
     * MutationObserver script reporting dynamically injected or lazy-loaded media nodes
     * through `window.EmreBridge.reportMedia`.
     */
    fun mutationObserverScript(): String {
        return """
            (function() {
                if (window.__emreObserverInstalled) return;
                window.__emreObserverInstalled = true;

                function report(u) {
                    if (!u || u.indexOf('data:') === 0 || u.indexOf('blob:') === 0) return;
                    try {
                        var abs = new URL(u, document.baseURI || window.location.href).href;
                        if (window.EmreBridge && window.EmreBridge.reportMedia) {
                            window.EmreBridge.reportMedia(abs);
                        }
                    } catch(e) {}
                }

                function checkNode(node) {
                    if (!node || node.nodeType !== 1) return;
                    var tag = node.tagName ? node.tagName.toUpperCase() : '';
                    if (tag === 'IMG') {
                        report(node.src);
                        report(node.getAttribute('data-src'));
                        report(node.getAttribute('data-original'));
                        report(node.getAttribute('data-lazy-src'));
                        report(node.getAttribute('data-large-file'));
                        report(node.getAttribute('data-hi-res-src'));
                        var srcset = node.getAttribute('srcset') || node.getAttribute('data-srcset');
                        if (srcset) {
                            var parts = srcset.split(',');
                            for (var i = 0; i < parts.length; i++) {
                                var bit = parts[i].trim().split(/\s+/)[0];
                                if (bit) report(bit);
                            }
                        }
                    } else if (tag === 'VIDEO') {
                        if (node.src && node.src.indexOf('blob:') !== 0 && node.src.indexOf('data:') !== 0) {
                            report(node.src);
                        } else if (node.querySelectorAll) {
                            var vSources = node.querySelectorAll('source');
                            for (var vs = 0; vs < vSources.length; vs++) {
                                var vsSrc = vSources[vs].src;
                                if (vsSrc && vsSrc.indexOf('blob:') !== 0 && vsSrc.indexOf('data:') !== 0) {
                                    report(vsSrc);
                                    break;
                                }
                            }
                        }
                        report(node.getAttribute('poster'));
                    } else if (tag === 'AUDIO') {
                        if (node.src && node.src.indexOf('blob:') !== 0 && node.src.indexOf('data:') !== 0) {
                            report(node.src);
                        } else if (node.querySelectorAll) {
                            var aSources = node.querySelectorAll('source');
                            for (var as = 0; as < aSources.length; as++) {
                                var asSrc = aSources[as].src;
                                if (asSrc && asSrc.indexOf('blob:') !== 0 && asSrc.indexOf('data:') !== 0) {
                                    report(asSrc);
                                    break;
                                }
                            }
                        }
                    } else if (tag === 'SOURCE') {
                        var parentTag = node.parentNode ? (node.parentNode.tagName || '').toUpperCase() : '';
                        if (parentTag !== 'VIDEO' && parentTag !== 'AUDIO') {
                            report(node.src);
                            report(node.getAttribute('srcset'));
                        }
                    }

                    if (node.querySelectorAll) {
                        var children = node.querySelectorAll('img, video, audio, source');
                        for (var c = 0; c < children.length; c++) {
                            checkNode(children[c]);
                        }
                    }
                }

                var observer = new MutationObserver(function(mutations) {
                    for (var m = 0; m < mutations.length; m++) {
                        var mut = mutations[m];
                        if (mut.type === 'childList') {
                            for (var i = 0; i < mut.addedNodes.length; i++) {
                                checkNode(mut.addedNodes[i]);
                            }
                        } else if (mut.type === 'attributes') {
                            checkNode(mut.target);
                        }
                    }
                });

                observer.observe(document.body || document.documentElement, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['src', 'data-src', 'data-original', 'data-lazy-src', 'srcset', 'data-srcset', 'poster']
                });
            })();
        """.trimIndent()
    }

    /**
     * Adaptive scrolling script reporting `scrollY`, `scrollHeight`, and `remaining` pixels.
     */
    fun adaptiveScrollScript(): String {
        return """
            (function() {
                var doc = document.documentElement || document.body;
                var scrollY = Math.round(window.pageYOffset || doc.scrollTop || 0);
                var innerHeight = window.innerHeight || 0;
                var scrollHeight = doc.scrollHeight || 0;
                var remaining = scrollHeight - (scrollY + innerHeight);

                var scrollDelta = Math.max(innerHeight * 0.85, 400);
                window.scrollBy({ top: scrollDelta, behavior: 'smooth' });
                window.dispatchEvent(new Event('scroll'));

                return JSON.stringify({
                    scrollY: scrollY,
                    scrollHeight: scrollHeight,
                    remaining: remaining
                });
            })();
        """.trimIndent()
    }

    /**
     * Hooks `window.fetch`, `XMLHttpRequest`, `MediaSource.prototype.addSourceBuffer`, and
     * `URL.createObjectURL` to capture media URLs returned inside SPA JSON/GraphQL API payloads
     * and substantial image blobs before they disappear into `blob:` URLs.
     */
    fun networkAndBlobHooksScript(): String {
        return """
            (function() {
                if (window.__emreNetworkHooksInstalled) return;
                window.__emreNetworkHooksInstalled = true;

                var reported = Object.create(null);
                var MAX_SCAN_BYTES = 2097152;
                var MAX_IMAGE_BLOB_BYTES = 2097152;
                var MIN_IMAGE_BLOB_BYTES = 4096;

                var MEDIA_URL_REGEX = /(?:https?:\\?\/\\?\/|\\?\/\\?\/)[^\s"'<>\\`]+(?:\.(?:mp4|webm|mov|mkv|m4v|m3u8|mpd|mp3|m4a|aac|ogg|opus|flac|jpg|jpeg|png|webp|gif|avif)(?:[?#][^\s"'<>\\`]*)?)/gi;
                var STREAM_OR_CDN_REGEX = /https?:\\?\/\\?\/[^\s"'<>\\`]+(?:videoplayback|twimg\.com\/media|video\.twimg\.com|fbcdn\.net|cdninstagram\.com|i\.redd\.it|v\.redd\.it|i\.pinimg\.com|i\.imgur\.com)[^\s"'<>\\`]*/gi;

                function safeReport(rawUrl, baseHref) {
                    if (!rawUrl || typeof rawUrl !== 'string') return;
                    if (!window.EmreBridge || !window.EmreBridge.reportMedia) return;
                    try {
                        var cleaned = rawUrl
                            .replace(/\\u002[fF]/g, '/')
                            .replace(/\\\//g, '/')
                            .replace(/\\u0026/g, '&')
                            .replace(/&amp;/g, '&')
                            .trim();
                        if (!cleaned) return;
                        if (cleaned.indexOf('data:') === 0) {
                            if (cleaned.length <= 2048 || cleaned.indexOf('svg') !== -1) return;
                            var dataSig = 'd:' + cleaned.length + ':' + cleaned.slice(0, 96);
                            if (reported[dataSig]) return;
                            reported[dataSig] = true;
                            window.EmreBridge.reportMedia(cleaned);
                            return;
                        }
                        if (cleaned.indexOf('blob:') === 0) return;
                        var abs = new URL(cleaned, baseHref || document.baseURI || window.location.href).href;
                        if (!abs || reported[abs]) return;
                        reported[abs] = true;
                        window.EmreBridge.reportMedia(abs);
                    } catch (e) {}
                }

                function isMediaContentType(ct) {
                    if (!ct) return false;
                    var lower = ct.toLowerCase();
                    return lower.indexOf('video/') === 0 ||
                        lower.indexOf('audio/') === 0 ||
                        lower.indexOf('image/') === 0 ||
                        lower.indexOf('mpegurl') !== -1 ||
                        lower.indexOf('dash+xml') !== -1;
                }

                function isInspectableTextType(ct) {
                    if (!ct) return false;
                    var lower = ct.toLowerCase();
                    return lower.indexOf('json') !== -1 ||
                        lower.indexOf('graphql') !== -1 ||
                        lower.indexOf('javascript') !== -1 ||
                        lower.indexOf('text/plain') !== -1 ||
                        lower.indexOf('mpegurl') !== -1 ||
                        lower.indexOf('dash+xml') !== -1 ||
                        lower.indexOf('xml') !== -1;
                }

                function scanTextForMedia(text, baseHref) {
                    if (!text || typeof text !== 'string') return;
                    if (text.length === 0 || text.length > MAX_SCAN_BYTES) return;
                    var trimmed = text.trim();
                    if (!trimmed) return;
                    var m1 = trimmed.match(MEDIA_URL_REGEX);
                    if (m1) {
                        for (var i = 0; i < m1.length && i < 120; i++) {
                            safeReport(m1[i], baseHref);
                        }
                    }
                    var m2 = trimmed.match(STREAM_OR_CDN_REGEX);
                    if (m2) {
                        for (var j = 0; j < m2.length && j < 120; j++) {
                            safeReport(m2[j], baseHref);
                        }
                    }
                }

                function scanPerformanceResources() {
                    try {
                        if (!window.performance || !window.performance.getEntriesByType) return;
                        var entries = window.performance.getEntriesByType('resource') || [];
                        var start = Math.max(0, entries.length - 60);
                        for (var i = start; i < entries.length; i++) {
                            var name = entries[i] && entries[i].name;
                            if (name && (MEDIA_URL_REGEX.test(name) || STREAM_OR_CDN_REGEX.test(name))) {
                                MEDIA_URL_REGEX.lastIndex = 0;
                                STREAM_OR_CDN_REGEX.lastIndex = 0;
                                safeReport(name, window.location.href);
                            } else {
                                MEDIA_URL_REGEX.lastIndex = 0;
                                STREAM_OR_CDN_REGEX.lastIndex = 0;
                            }
                        }
                    } catch (e) {}
                }

                if (typeof window.fetch === 'function') {
                    var origFetch = window.fetch;
                    window.fetch = function(input, init) {
                        var reqUrl = '';
                        try {
                            reqUrl = (typeof input === 'string') ? input : (input && input.url ? input.url : '');
                        } catch (e) {}
                        return origFetch.apply(this, arguments).then(function(response) {
                            try {
                                var respUrl = (response && response.url) ? response.url : reqUrl;
                                var ct = response && response.headers ? (response.headers.get('content-type') || '') : '';
                                if (isMediaContentType(ct)) {
                                    safeReport(respUrl, window.location.href);
                                } else if (isInspectableTextType(ct) && typeof response.clone === 'function') {
                                    var cl = parseInt(response.headers.get('content-length') || '0', 10);
                                    if (!cl || cl <= MAX_SCAN_BYTES) {
                                        response.clone().text().then(function(bodyText) {
                                            scanTextForMedia(bodyText, respUrl || window.location.href);
                                        }).catch(function() {});
                                    }
                                }
                            } catch (e) {}
                            return response;
                        });
                    };
                }

                if (window.XMLHttpRequest && window.XMLHttpRequest.prototype) {
                    var origOpen = window.XMLHttpRequest.prototype.open;
                    var origSend = window.XMLHttpRequest.prototype.send;

                    window.XMLHttpRequest.prototype.open = function(method, url) {
                        try {
                            this.__emreReqUrl = (typeof url === 'string') ? url : String(url || '');
                        } catch (e) {}
                        return origOpen.apply(this, arguments);
                    };

                    window.XMLHttpRequest.prototype.send = function() {
                        try {
                            var xhr = this;
                            if (!xhr.__emreHooked) {
                                xhr.__emreHooked = true;
                                xhr.addEventListener('load', function() {
                                    try {
                                        var respUrl = xhr.responseURL || xhr.__emreReqUrl || '';
                                        var ct = (xhr.getResponseHeader && xhr.getResponseHeader('content-type')) || '';
                                        if (isMediaContentType(ct)) {
                                            safeReport(respUrl, window.location.href);
                                            return;
                                        }
                                        if (xhr.responseType === '' || xhr.responseType === 'text') {
                                            var txt = xhr.responseText;
                                            if (txt && (isInspectableTextType(ct) || txt.charAt(0) === '{' || txt.charAt(0) === '[')) {
                                                scanTextForMedia(txt, respUrl || window.location.href);
                                            }
                                        } else if (xhr.responseType === 'json' && xhr.response) {
                                            scanTextForMedia(JSON.stringify(xhr.response), respUrl || window.location.href);
                                        }
                                    } catch (e) {}
                                });
                            }
                        } catch (e) {}
                        return origSend.apply(this, arguments);
                    };
                }

                if (window.MediaSource && window.MediaSource.prototype && window.MediaSource.prototype.addSourceBuffer) {
                    var origAddSourceBuffer = window.MediaSource.prototype.addSourceBuffer;
                    window.MediaSource.prototype.addSourceBuffer = function(mimeType) {
                        try {
                            this.__emreIsMse = true;
                            this.__emreMseMime = mimeType;
                            scanPerformanceResources();
                        } catch (e) {}
                        return origAddSourceBuffer.apply(this, arguments);
                    };
                }

                if (window.URL && typeof window.URL.createObjectURL === 'function') {
                    var origCreateObjectURL = window.URL.createObjectURL.bind(window.URL);
                    window.URL.createObjectURL = function(obj) {
                        var blobUrl = origCreateObjectURL(obj);
                        try {
                            if (window.Blob && obj instanceof window.Blob) {
                                var bType = (obj.type || '').toLowerCase();
                                if (bType.indexOf('image/') === 0 && bType.indexOf('svg') === -1 &&
                                    obj.size > MIN_IMAGE_BLOB_BYTES && obj.size <= MAX_IMAGE_BLOB_BYTES &&
                                    typeof FileReader === 'function') {
                                    var reader = new FileReader();
                                    reader.onloadend = function() {
                                        if (typeof reader.result === 'string') {
                                            safeReport(reader.result, window.location.href);
                                        }
                                    };
                                    reader.readAsDataURL(obj);
                                } else if (bType.indexOf('video/') === 0 || bType.indexOf('audio/') === 0) {
                                    scanPerformanceResources();
                                }
                            } else if (window.MediaSource && (obj instanceof window.MediaSource || (obj && obj.__emreIsMse))) {
                                scanPerformanceResources();
                            }
                        } catch (e) {}
                        return blobUrl;
                    };
                }
            })();
        """.trimIndent()
    }
}
