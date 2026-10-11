#!/usr/bin/env python3
"""Headless differential audit of exact source-extracted native methods.

Compiles current Battle initializer/tick, GPU queue/copy and copy-command sources
inside a minimal CPU shell. This avoids initializing the game or graphics system.
It proves those method bodies against private inputs, not a full native game run.
The temporary binary input contains original words and is never published.
"""
import hashlib
import importlib.util
import json
import struct
import subprocess
import tempfile
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('family_discovery', ROOT/'scripts/discover-envhd-battle-families.py')
discovery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(discovery)


def copy_words(words, provenance, command):
    sx, sy, dx, dy, width, height = command
    if min(sx, sy, dx, dy, width, height) < 0 or max(sx + width, dx + width) > 1024 or max(sy + height, dy + height) > 512:
        raise ValueError('Copy outside VRAM')
    if width == 0 or height == 0:
        return 0
    unknown = 0
    if not discovery.battle.overlaps([sx, sy, width, height], [dx, dy, width, height]):
        src, dst = np.s_[sy:sy + height, sx:sx + width], np.s_[dy:dy + height, dx:dx + width]
        unknown = int(np.count_nonzero(provenance[src] < 0))
        words[dst], provenance[dst] = words[src], provenance[src]
    else:
        # Gpu.copyVramToVram reads/writes y then x, not a snapshot/memmove.
        for y in range(height):
            for x in range(width):
                unknown += int(provenance[sy + y, sx + x] < 0)
                words[dy + y, dx + x] = words[sy + y, sx + x]
                provenance[dy + y, dx + x] = provenance[sy + y, sx + x]
    return unknown


def case_slots(case):
    return case.get('sequences', [case])


def python_receipt(case):
    slots = sorted(case_slots(case), key=lambda s: s['slot'])
    words = np.zeros((512, 1024), dtype=np.uint16)
    provenance = np.full((512, 1024), -1, dtype=np.int32)
    original, states = {}, {}
    for slot in slots:
        values = slot['values']
        discovery.validate_sequence(values)
        area = discovery.selection([values[0], values[1], values[2] // 4, values[3]])
        if np.any(provenance[area] >= 0):
            raise ValueError('Differential shell requires disjoint slots')
        words[area] = slot['sourceWords']
        addresses = np.arange(512 * 1024, dtype=np.int32).reshape(512, 1024)
        provenance[area] = addresses[area]
        original[slot['slot']] = words[area].copy()
        states[slot['slot']] = discovery.initial_state(values)
    sha, unknown_reads = hashlib.sha256(), 0
    for _ in range(case['ticks']):
        by_slot = {}
        for slot in slots:
            ident = slot['slot']
            states[ident], by_slot[ident] = discovery.advance(slot['values'], states[ident])
            sha.update(struct.pack('>3i', *states[ident][:3]))
        commands = [command for slot in reversed(slots) for command in by_slot[slot['slot']]]
        sha.update(struct.pack('>i', len(commands)))
        for command in commands:
            sha.update(struct.pack('>6i', *command))
            unknown_reads += copy_words(words, provenance, command)
        for slot in slots:
            x, y, width, height = slot['values'][:4]
            width //= 4
            image, scratch = np.s_[y:y + height, x:x + width], np.s_[256:256 + height, 960:960 + width]
            # Differential copy replay also independently checks the optimized
            # family decoder's row-rotation model on every invocation.
            if not np.array_equal(words[image], np.roll(original[slot['slot']], states[slot['slot']][3], axis=0)):
                raise ValueError('Ordered native copies differ from rotation model')
            for area in (image, scratch):
                sha.update(words[area].astype('>i4').tobytes())
                sha.update(provenance[area].astype('>i4').tobytes())
    return dict(sha256=sha.hexdigest(), unknownReads=unknown_reads)


def java_shell():
    battle_path = ROOT/'src/main/java/legend/game/combat/Battle.java'
    gpu_path = ROOT/'src/main/java/legend/core/gpu/Gpu.java'
    initializer = discovery.method_body(battle_path, 'public void FUN_800ec86c(')
    apply = discovery.method_body(battle_path, 'public void applyBattleStageTextureAnimations(')
    copy = discovery.method_body(gpu_path, 'public void copyVramToVram(')
    queue = discovery.method_body(gpu_path, 'public void queueCommand(final int z,')
    tick = discovery.method_body(gpu_path, 'private void tick()')
    flush_start = tick.index('if(this.zQueues != null)')
    flush_end = tick.index('this.drawBufferIndex')
    flush = tick[flush_start:flush_end]
    render = discovery.method_body(battle_path, 'public void renderBattleStage(')
    loop = render[render.index('for(int i = 0; i < 10; i++)'):render.index('tmdGp0Tpage_')]
    command_text = (ROOT/'src/main/java/legend/core/gpu/GpuCommandCopyVramToVram.java').read_text()
    command = command_text[command_text.index('public class GpuCommandCopyVramToVram'):].replace('public class ', 'static class ', 1)
    prefix = r'''
import java.io.*;
import java.nio.*;
import java.security.*;
import java.util.*;
public class NativeReplay {
  static Gpu GPU;
  static class BattleStage {
    short[][] _5f0 = new short[10][];
    int[] _618 = new int[10], _622 = new int[10], _64a = new int[10], _65e = new int[10];
  }
  static class NativeBattle {
'''
    middle = r'''
  }
  static abstract class GpuCommand { public abstract void render(Gpu gpu); }
'''
    gpu_prefix = r'''
  static class Gpu {
    enum DRAW_PIXELS { ALWAYS, NOT_TO_MASKED_AREAS }
    static class Status { boolean setMaskBit; DRAW_PIXELS drawPixels = DRAW_PIXELS.ALWAYS; }
    static class Logger { boolean isDebugEnabled() { return false; } void debug(String fmt, Object... args) {} }
    final Logger LOGGER = new Logger();
    final Object vramLock = new Object();
    final Status status = new Status();
    final int[] words = new int[1024*512], provenance = new int[1024*512];
    final List<GpuCommand>[] zQueues;
    boolean vramDirty;
    long unknownReads;
    class Detail { void copyWord(int src, int dst) { if(provenance[src] < 0) unknownReads++; provenance[dst] = provenance[src]; } }
    final Detail effectVram = new Detail();
    @SuppressWarnings("unchecked") Gpu() { Arrays.fill(provenance, -1); zQueues = new List[8]; Arrays.setAll(zQueues, i -> new ArrayList<>()); }
    int getPixel15(int x, int y) { return words[y*1024+x]; }
    int getPixel(int x, int y) { return (words[y*1024+x] & 0x8000) == 0 ? 0 : 0x1000000; }
    void setVramPixel(int x, int y, int colour24, int colour15, boolean detail) { words[y*1024+x] = colour15; }
'''
    suffix = r'''
  }
  static void addInt(MessageDigest sha, int value) {
    sha.update((byte)(value >>> 24)); sha.update((byte)(value >>> 16)); sha.update((byte)(value >>> 8)); sha.update((byte)value);
  }
  static void area(MessageDigest sha, int[] data, int x, int y, int w, int h) {
    for(int yy = 0; yy < h; yy++) for(int xx = 0; xx < w; xx++) addInt(sha, data[(y+yy)*1024+x+xx]);
  }
  public static void main(String[] args) throws Exception {
    try(DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(args[0])))) {
      int count = input.readInt();
      for(int c = 0; c < count; c++) {
        GPU = new Gpu(); NativeBattle battle = new NativeBattle(); BattleStage stage = new BattleStage();
        int ticks = input.readInt(), n = input.readInt(); int[] slots = new int[n];
        for(int i = 0; i < n; i++) {
          int slot = input.readInt(), length = input.readInt(); slots[i] = slot;
          short[] sequence = new short[length]; for(int j = 0; j < length; j++) sequence[j] = input.readShort();
          stage._5f0[slot] = sequence; battle.FUN_800ec86c(stage, slot);
          int x = sequence[0], y = sequence[1], w = sequence[2]/4, h = sequence[3];
          for(int yy = 0; yy < h; yy++) for(int xx = 0; xx < w; xx++) {
            int address = (y+yy)*1024+x+xx; GPU.words[address] = input.readUnsignedShort(); GPU.provenance[address] = address;
          }
        }
        Arrays.sort(slots); MessageDigest sha = MessageDigest.getInstance("SHA-256");
        for(int t = 0; t < ticks; t++) {
          battle.renderAnimationLoop(stage);
          for(int slot: slots) { addInt(sha, stage._622[slot]); addInt(sha, stage._64a[slot]); addInt(sha, stage._65e[slot]); }
          List<GpuCommand> queue = GPU.zQueues[1]; addInt(sha, queue.size());
          for(int i = queue.size()-1; i >= 0; i--) {
            GpuCommandCopyVramToVram cmd = (GpuCommandCopyVramToVram)queue.get(i);
            for(int value: new int[]{cmd.sourceX,cmd.sourceY,cmd.destX,cmd.destY,cmd.width,cmd.height}) addInt(sha,value);
          }
          GPU.flush();
          for(int slot: slots) {
            short[] s = stage._5f0[slot]; int x=s[0],y=s[1],w=s[2]/4,h=s[3];
            area(sha,GPU.words,x,y,w,h); area(sha,GPU.provenance,x,y,w,h);
            area(sha,GPU.words,960,256,w,h); area(sha,GPU.provenance,960,256,w,h);
          }
        }
        System.out.println(HexFormat.of().formatHex(sha.digest()) + " " + GPU.unknownReads);
      }
      if(input.read() != -1) throw new IOException("Trailing private input");
    }
  }
}
'''
    return prefix + initializer + '\n' + apply + '\nvoid renderAnimationLoop(BattleStage stage) {\n' + loop + '\n}\n' + middle + command + gpu_prefix + copy + '\n' + queue + '\nvoid flush() {\n' + flush + '\n}\n' + suffix


def write_input(path, cases):
    with path.open('xb') as output:
        output.write(struct.pack('>i', len(cases)))
        for case in cases:
            slots = case_slots(case)
            output.write(struct.pack('>2i', case['ticks'], len(slots)))
            for slot in slots:
                discovery.validate_sequence(slot['values'])
                output.write(struct.pack('>2i', slot['slot'], len(slot['values'])))
                output.write(struct.pack('>' + 'h' * len(slot['values']), *slot['values']))
                output.write(slot['sourceWords'].astype('>u2').tobytes())


def verify(cases, java_home):
    shell = java_shell()
    expected = [python_receipt(case) for case in cases]
    with tempfile.TemporaryDirectory(prefix='envhd-private-native-cycle-') as directory:
        root = Path(directory)
        source, payload = root/'NativeReplay.java', root/'private-original-words.bin'
        source.write_text(shell)
        write_input(payload, cases)
        compiled = subprocess.run([str(java_home/'bin/javac'), str(source)], capture_output=True, text=True, timeout=60)
        if compiled.returncode:
            raise ValueError('Native CPU shell compilation failed: ' + compiled.stderr[:2000])
        executed = subprocess.run([str(java_home/'bin/java'), '-Djava.awt.headless=true', '-cp', str(root), 'NativeReplay', str(payload)], capture_output=True, text=True, timeout=180)
        if executed.returncode:
            raise ValueError('Native CPU differential failed: ' + executed.stderr[:2000])
        actual = [dict(sha256=line.split()[0], unknownReads=int(line.split()[1])) for line in executed.stdout.splitlines()]
    if actual != expected or any(r['unknownReads'] for r in actual):
        raise ValueError('Native/Python state, ordered-copy, pixel or provenance mismatch')
    return dict(method='source-extracted-current-Java-methods-in-minimal-headless-CPU-shell',
                cases=len(cases), invocations=sum(c['ticks'] for c in cases),
                stateAndCopyAndPixelAndProvenanceReceiptsSha256=discovery.json_digest(actual),
                compiledShellSha256=hashlib.sha256(shell.encode()).hexdigest(),
                unknownSourceReads=0, matched=True,
                limitation='Does not launch the game, execute complete encounter scripts, render graphics or prove gameplay/final artwork acceptance.')
