#!/usr/bin/env python3
"""
Headless GLSL harness: runs the exact Cosmos shader sources (shaders/*.fsh) on an OpenGL 3.3+ context
(Mesa llvmpipe via EGL here; any GPU on a real machine) and writes PNGs, so the visuals can be inspected
without Minecraft. The Fabric renderer uses the same files.
"""
import os, re, struct, sys
import numpy as np
import moderngl
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SHADERS = os.path.join(HERE, "..", "shaders")

VERT = """#version 330 core
in vec2 aPos; out vec2 vUv;
void main() { vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }
"""

def load_shader(name):
    src = open(os.path.join(SHADERS, name)).read()
    def inc(m):
        return open(os.path.join(SHADERS, m.group(1))).read()
    # resolve #include once (common.glsl has its own guard)
    for _ in range(3):
        src = re.sub(r'#include\s+"([^"]+)"', inc, src)
    # GLSL requires #version first; included files must not carry it
    return src

def context():
    return moderngl.create_standalone_context(backend="egl", require=330)

def load_f32(path):
    with open(path, "rb") as f:
        w, h = struct.unpack("<ii", f.read(8))
        data = np.frombuffer(f.read(), dtype="<f4").reshape(h, w, 3)
    return w, h, data

def sky_texture(ctx, path):
    w, h, data = load_f32(path)
    tex = ctx.texture((w, h), 3, data.astype("f4").tobytes(), dtype="f4")
    tex.filter = (moderngl.LINEAR, moderngl.LINEAR)
    tex.repeat_x = True; tex.repeat_y = False
    return tex

def camera_basis(forward, up_hint):
    f = np.array(forward, dtype=float); f /= np.linalg.norm(f)
    r = np.cross(f, up_hint); r /= np.linalg.norm(r)
    u = np.cross(r, f)
    return np.stack([r, u, -f], axis=1)  # columns

def render(ctx, frag_name, size, uniforms, textures=None, out=None):
    w, h = size
    prog = ctx.program(vertex_shader=VERT, fragment_shader=load_shader(frag_name))
    vbo = ctx.buffer(np.array([-1, -1, 1, -1, -1, 1, 1, 1], dtype="f4").tobytes())
    vao = ctx.vertex_array(prog, [(vbo, "2f", "aPos")])
    fbo = ctx.framebuffer(color_attachments=[ctx.texture((w, h), 4)])
    fbo.use()
    for k, v in uniforms.items():
        if k not in prog:
            continue
        if isinstance(v, np.ndarray) and v.shape == (3, 3):
            prog[k].write(v.T.astype("f4").tobytes())  # column-major for GLSL
        elif isinstance(v, (tuple, list, np.ndarray)):
            prog[k].value = tuple(float(x) for x in v)
        else:
            prog[k].value = v
    for unit, (name, tex) in enumerate((textures or {}).items()):
        tex.use(unit)
        if name in prog:
            prog[name].value = unit
    vao.render(moderngl.TRIANGLE_STRIP)
    img = Image.frombytes("RGBA", (w, h), fbo.color_attachments[0].read()).transpose(Image.FLIP_TOP_BOTTOM).convert("RGB")
    if out:
        img.save(out)
    return img

# ---------------------------------------------------------------- multi-pass HDR helpers
class HdrScene:
    """Float framebuffer the passes draw into, then composite.fsh tonemaps it."""
    def __init__(self, ctx, size):
        self.ctx, self.size = ctx, size
        self.tex = ctx.texture(size, 4, dtype="f4")
        self.tex.filter = (moderngl.LINEAR, moderngl.LINEAR)
        self.fbo = ctx.framebuffer(color_attachments=[self.tex])
        self.quad = ctx.buffer(np.array([-1, -1, 1, -1, -1, 1, 1, 1], dtype="f4").tobytes())
        self.fbo.use(); ctx.clear(0, 0, 0, 1)

    def _set(self, prog, uniforms):
        for k, v in uniforms.items():
            if k not in prog: continue
            if isinstance(v, np.ndarray) and v.shape == (3, 3): prog[k].write(v.T.astype("f4").tobytes())
            elif isinstance(v, (tuple, list, np.ndarray)): prog[k].value = tuple(float(x) for x in v)
            else: prog[k].value = v

    def fullscreen(self, frag, uniforms, textures=None, blend=False):
        prog = self.ctx.program(vertex_shader=VERT, fragment_shader=load_shader(frag))
        self._set(prog, uniforms)
        for unit, (name, tex) in enumerate((textures or {}).items()):
            tex.use(unit)
            if name in prog: prog[name].value = unit
        self.fbo.use()
        if blend:
            self.ctx.enable(moderngl.BLEND); self.ctx.blend_func = (moderngl.ONE, moderngl.ONE)
        else:
            self.ctx.disable(moderngl.BLEND)
        self.ctx.vertex_array(prog, [(self.quad, "2f", "aPos")]).render(moderngl.TRIANGLE_STRIP)
        self.ctx.disable(moderngl.BLEND)

    def stars(self, star_bin, uniforms):
        data = open(star_bin, "rb").read()
        n = struct.unpack("<i", data[:4])[0]
        vbo = self.ctx.buffer(data[4:4 + n * 28])
        prog = self.ctx.program(vertex_shader=load_shader("stars.vsh"), fragment_shader=load_shader("stars.fsh"))
        self._set(prog, uniforms)
        self.fbo.use()
        self.ctx.enable(moderngl.BLEND | moderngl.PROGRAM_POINT_SIZE); self.ctx.blend_func = (moderngl.ONE, moderngl.ONE)
        self.ctx.vertex_array(prog, [(vbo, "3f 1f 3f", "aDir", "aMag", "aColor")]).render(moderngl.POINTS)
        self.ctx.disable(moderngl.BLEND)

    def save(self, out, exposure=1.0, bloom=0.6):
        w, h = self.size
        prog = self.ctx.program(vertex_shader=VERT, fragment_shader=load_shader("composite.fsh"))
        self._set(prog, {"uResolution": (w, h), "uExposure": exposure, "uBloom": bloom})
        self.tex.build_mipmaps(); self.tex.filter = (moderngl.LINEAR_MIPMAP_LINEAR, moderngl.LINEAR)
        self.tex.use(0); prog["uHdr"].value = 0
        ldr = self.ctx.framebuffer(color_attachments=[self.ctx.texture((w, h), 4)])
        ldr.use()
        self.ctx.vertex_array(prog, [(self.quad, "2f", "aPos")]).render(moderngl.TRIANGLE_STRIP)
        img = Image.frombytes("RGBA", (w, h), ldr.color_attachments[0].read()).transpose(Image.FLIP_TOP_BOTTOM).convert("RGB")
        img.save(out); return img

def radec(ra, dec):
    ra, dec = np.radians(ra), np.radians(dec)
    return np.array([np.cos(dec) * np.cos(ra), np.cos(dec) * np.sin(ra), np.sin(dec)])
