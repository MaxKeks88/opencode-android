import os
import sys

# На Android ELF-интерпретатор /lib/ld-musl-aarch64.so.1 отсутствует,
# поэтому дочерние процессы Python нужно запускать через нашу shell-обёртку.
_w = os.environ.get("OC_PYTHON_WRAPPER")
if _w:
    sys.executable = _w

# musl: разные пакеты публикуют колёса под musllinux_1_1 и musllinux_1_2.
# pip строит теги через _expand_allowed_platforms — добавляем второй набор.
try:
    import pip._internal.utils.compatibility_tags as _ct

    if not getattr(_ct, "_oc_plat_patched", False):
        _orig_exp = _ct._expand_allowed_platforms

        def _expand(platforms):
            res = _orig_exp(platforms)
            if res is None:
                import sysconfig as _sc
                base = _sc.get_platform().replace("-", "_").replace(".", "_")
                res = [base]
            res = list(res)
            extra = []
            for p in res:
                if "musllinux_1_2" in p:
                    q = p.replace("musllinux_1_2", "musllinux_1_1")
                    if q not in res:
                        extra.append(q)
            return res + extra

        _ct._expand_allowed_platforms = _expand
        _ct._oc_plat_patched = True
except Exception:
    pass

# На случай прямых вызовов sys_tags() (не только pip).
try:
    import pip._vendor.packaging.tags as _tags

    if not getattr(_tags, "_oc_patched", False):
        _orig = _tags.sys_tags

        def _sys_tags():
            seen = set()
            for t in _orig():
                if t not in seen:
                    seen.add(t)
                    yield t
            for t in list(_orig()):
                plat = t.platform
                if "musllinux_1_2" in plat:
                    p2 = plat.replace("musllinux_1_2", "musllinux_1_1")
                    try:
                        nt = _tags.Tag(t.interpreter, t.abi, p2)
                    except Exception:
                        continue
                    if nt not in seen:
                        seen.add(nt)
                        yield nt

        _tags.sys_tags = _sys_tags
        _tags._oc_patched = True
except Exception:
    pass
