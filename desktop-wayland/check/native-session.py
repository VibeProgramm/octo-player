#!/usr/bin/python3
"""Run inside the isolated KWin session described in README.md."""
import json
import os
import re
import signal
from pathlib import Path
import shutil
import subprocess
import time
from fixture_server import Fixture

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(os.environ.get('OCTO_CHECK_OUT', '/tmp/opencode/octo-native-player-check'))
OUT.mkdir(parents=True, exist_ok=True)
assert os.environ.get('WAYLAND_DISPLAY', '').startswith('wayland-octo'), 'Use an isolated test compositor'
INPUT = os.environ.get('OCTO_CHECK_INPUT', '/tmp/opencode/octo-input')
LAUNCHER = os.environ.get('OCTO_WAYLAND_LAUNCHER', str(ROOT / 'desktop-wayland/build/install/Octo-Wayland/octo-wayland'))


def run(*args, **kwargs):
    p = subprocess.run(args, text=True, capture_output=True, timeout=20, **kwargs)
    if p.returncode:
        raise RuntimeError(f'{args}: {p.stderr} {p.stdout}')
    return p.stdout.strip()


def wait(test, message, seconds=40):
    until = time.monotonic() + seconds
    while time.monotonic() < until:
        if test():
            return
        time.sleep(.1)
    raise AssertionError(message)


def script(body):
    path = OUT / 'window.js'
    marker = f'OCTO-SCRIPT-{time.time_ns()}'
    path.write_text(body + '\nconsole.warn(' + json.dumps(marker) + ');')
    number = run('qdbus6', 'org.kde.KWin', '/Scripting', 'org.kde.kwin.Scripting.loadScript', str(path), 'octo-check')
    paths = run('qdbus6', 'org.kde.KWin').splitlines()
    obj = next(p.strip() for p in paths if p.strip().endswith('/Script' + number) or p.strip() == '/' + number)
    run('qdbus6', 'org.kde.KWin', obj, 'org.kde.kwin.Script.run')
    # run() starts an asynchronous script load. Do not unload it until the
    # compositor has actually completed the requested window operations.
    wait(lambda: marker in (OUT / 'compositor.log').read_text(), 'KWin script did not complete')
    run('qdbus6', 'org.kde.KWin', '/Scripting', 'org.kde.kwin.Scripting.unloadScript', 'octo-check')


def move(x=30, y=30, width=1100, height=830):
    script(f'''for (const w of workspace.windowList()) if (String(w.resourceClass) === "app.winters.octo.wayland") {{
        w.frameGeometry = {{x:{x}, y:{y}, width:{width}, height:{height}}};
        workspace.activeWindow = w;
    }}''')
    time.sleep(.5)


def input(*events):
    run(INPUT, *[value for event in events for value in (event, 'pause=50')])


def key(code):
    input(f'key={code},1', f'key={code},0')


def ctrl(code):
    input('key=29,1', f'key={code},1', f'key={code},0', 'key=29,0')


def copied_text(expected):
    last = None
    def matches():
        nonlocal last
        result = subprocess.run(['wl-paste', '--no-newline'], text=True, capture_output=True, timeout=5)
        last = result.stdout if result.returncode == 0 else result.stderr
        return result.returncode == 0 and result.stdout == expected
    try:
        wait(matches, f'Clipboard did not receive {expected!r}')
    except AssertionError:
        capture('clipboard-failed')
        raise AssertionError(f'Expected clipboard {expected!r}, got {last!r}') from None


def stop(process):
    if process is None or process.poll() is not None:
        return
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=5)


def type_text(text):
    for char in text:
        sym = ord(char) if ord(char) < 256 else 0x1000000 + ord(char)
        input(f'sym={sym},1', f'sym={sym},0')


def paste(text):
    subprocess.run(['wl-copy', '--type', 'text/plain;charset=utf-8', text], check=True, timeout=10,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(.2)
    ctrl(47)
    time.sleep(.3)


def click(x, y):
    input(f'move={x},{y}', 'button=272,1', 'button=272,0')


def capture(name):
    image = OUT / 'frame.png'
    before = image.stat().st_mtime_ns if image.exists() else 0
    key(88)  # F12: explicit diagnostic capture property only.
    wait(lambda: image.exists() and image.stat().st_mtime_ns != before, 'No GPU readback')
    shutil.copyfile(image, OUT / (name + '.png'))


def media(property):
    return run('qdbus6', 'org.mpris.MediaPlayer2.octo', '/org/mpris/MediaPlayer2',
               'org.freedesktop.DBus.Properties.Get', 'org.mpris.MediaPlayer2.Player', property)


def ime(command):
    with open(os.environ['OCTO_IME_CONTROL'], 'w') as pipe:
        pipe.write(command + '\n')
    time.sleep(.4)


def portal_ready(title):
    # Observe the actual mapped dialog, rather than type after an arbitrary
    # delay into whichever application happens to hold keyboard focus.
    marker = f'OCTO-DIALOG-{time.time_ns()}'
    def mapped():
        script('for (const w of workspace.windowList()) if (String(w.caption).includes('
               + json.dumps(title) + ')) {workspace.activeWindow=w; console.warn(' + json.dumps(marker) + ');}')
        return marker in (OUT / 'compositor.log').read_text()
    wait(mapped, f'No isolated portal dialog: {title}')


def check_portals(env):
    target = OUT / f'Portal Unicode Файл-{time.time_ns()}.txt'
    process = None
    try:
        with (OUT / 'portal.log').open('w') as log:
            process = subprocess.Popen([LAUNCHER], env=dict(env, JAVA_TOOL_OPTIONS='-Docto.wayland.checkFiles=true'),
                                       stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            portal_text = lambda: (OUT / 'portal.log').read_text()
            wait(lambda: 'check-files save' in portal_text(), 'No portal save request')
            portal_ready('Octo check: Save')
            ctrl(30)
            paste(str(target))
            key(28)
            wait(target.exists, 'Portal Save did not return the selected file')
            wait(lambda: 'check-files open' in portal_text(), 'No portal open request')
            portal_ready('Octo check: Open')
            ctrl(30)  # The native file dialog initially focuses its filename.
            paste(str(target))
            key(28)
            wait(lambda: 'check-files PASS' in portal_text(), 'Portal Open did not return the saved Unicode path')
            assert process.wait(timeout=15) == 0
            print('PASS real portal Save/Open roundtrip with a Unicode filename', flush=True)
    finally:
        stop(process)


if os.environ.get('OCTO_CHECK_PORTAL_ONLY') == '1':
    check_portals(dict(os.environ, OCTO_PROFILE_DIR=str(OUT / f'portal-profile-{time.time_ns()}')))
    print('PASS native acceptance complete', flush=True)
    raise SystemExit(0)


fixture = Fixture()
process = None
source = None
try:
    outputs = json.loads(run('kscreen-doctor', '-j'))['outputs']
    a, b = [str(o['id']) for o in outputs]
    run('kscreen-doctor', f'output.{a}.scale.1', f'output.{a}.position.0,0', f'output.{b}.scale.1.5', f'output.{b}.position.1280,0')
    profile = OUT / f'profile-{time.time_ns()}'
    env = dict(os.environ, OCTO_PROFILE_DIR=str(profile), JAVA_HOME=os.environ.get('JAVA_HOME', '/usr/lib/jvm/java-27-openjdk'),
               JAVA_TOOL_OPTIONS=f'-Docto.wayland.capture={OUT}/frame.png -Docto.wayland.seconds=240', GTK_A11Y='none', NO_AT_BRIDGE='1')
    env.pop('DISPLAY', None)
    with (OUT / 'player.log').open('w') as log:
        command = [LAUNCHER]
        if os.environ.get('OCTO_CHECK_TRACE') == '1':
            command = ['strace', '-f', '-e', 'trace=connect,openat', '-o', str(OUT / 'trace.log'), *command]
        (OUT / 'frame.png').unlink(missing_ok=True)
        process = subprocess.Popen(command, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        text = lambda: (OUT / 'player.log').read_text()
        wait(lambda: 'player-ready audio=native' in text() and 'wayland: gpu=' in text()
             and 'focus id=1 focused=true' in text() and 'wayland: captured=' in text(),
             'No mapped, focused GPU window', seconds=70)
        move()
        wait(lambda: 'configure id=1 size=1100x802' in text(), 'Initial window resize was not acknowledged')
        capture('initial')
        ctrl(30)  # Select the server address.
        for k in (24, 46, 20, 24):
            key(k)
        key(14)
        ctrl(30)
        ctrl(46)
        copied_text('oct')
        print('PASS real Compose text field: keyboard, backspace, selection and copy', flush=True)
        paste('Привет 🌊')
        ctrl(30)
        ctrl(46)
        copied_text('Привет 🌊')
        print('PASS real Compose UTF-8 clipboard roundtrip', flush=True)
        if os.environ.get('OCTO_IME_CONTROL'):
            key(14)
            ime('preedit=に')
            capture('ime-preedit')
            ime('preedit=日本🌊')
            ime('commit=日本🌊')
            ctrl(30)
            ctrl(46)
            copied_text('日本🌊')
            key(107)  # End.
            ime('delete=4,0')
            ime('commit=語')
            ctrl(30)
            ctrl(46)
            copied_text('日本語')
            print('PASS real text-input-v3 preedit, commit and UTF-8 deletion', flush=True)
        paste(fixture.address)
        key(15)
        type_text('listener')
        key(15)
        type_text('secret')
        capture('signin')
        # Keep the fixture's password out of the desktop keyring.
        click(770, 596)
        click(710, 712)
        time.sleep(3)
        capture('signin-response')
        wait(lambda: any(e == 'getAlbumList2' for e, _, _ in fixture.calls), 'Sign-in/library loading did not reach the server')
        time.sleep(3)
        capture('library')
        print('PASS native sign-in and library HTTP requests', flush=True)
        ctrl(37)  # Ctrl+K.
        paste('Native')
        ctrl(30)
        ctrl(46)
        capture('search')
        copied_text('Native')
        time.sleep(2)
        capture('search')
        print('PASS search field input', flush=True)
        key(28)
        wait(lambda: any(e == 'stream' for e, _, _ in fixture.calls), 'No real HTTP audio stream')
        wait(lambda: media('PlaybackStatus') == 'Playing', 'Native engine did not start playing')
        first = int(media('Position'))
        time.sleep(1.5)
        assert int(media('Position')) - first > 500_000
        capture('playing')
        print('PASS search -> real HTTP playback, advancing MPRIS clock', flush=True)
        # Open the full player by its cover in the player bar.
        click(420, 803)
        time.sleep(.5)
        capture('full-player')
        key(57)  # Space pauses outside a text field.
        wait(lambda: media('PlaybackStatus') == 'Paused', 'Space did not pause')
        position = int(media('Position'))
        time.sleep(.7)
        assert abs(int(media('Position')) - position) < 150_000, 'Paused audio clock kept advancing'
        if os.environ.get('OCTO_CHECK_PERFORMANCE') == '1':
            pid = int(re.search(r'pid=(\d+)', text()).group(1))
            def ticks():
                fields = Path(f'/proc/{pid}/stat').read_text().split()
                return int(fields[13]) + int(fields[14])
            before = ticks()
            start = time.monotonic()
            time.sleep(8)
            cpu = 100 * (ticks() - before) / os.sysconf('SC_CLK_TCK') / (time.monotonic() - start)
            (OUT / 'performance.json').write_text(json.dumps({'paused_full_player_cpu_percent_of_one_core': cpu}))
            print(f'INFO paused full player CPU: {cpu:.1f}% of one core', flush=True)
        key(57)
        wait(lambda: media('PlaybackStatus') == 'Playing', 'Space did not resume')
        position = int(media('Position'))
        key(106)  # Seek forward.
        wait(lambda: int(media('Position')) > position + 4_000_000, 'Arrow did not seek')
        print('PASS native pause/resume and seeking', flush=True)
        ctrl(50)  # Ctrl+M.
        wait(lambda: 'closed id=1' in text(), 'Mini player did not replace main window')
        time.sleep(2)
        capture('mini')
        assert media('PlaybackStatus') == 'Playing'
        key(1)
        wait(lambda: 'closed id=2' in text(), 'Mini player did not close')
        wait(lambda: 'focus id=3 focused=true' in text(), 'Main window did not regain focus')
        changes = text().count('rawScale=1.5')
        move(1320, 30, 800, 750)
        wait(lambda: text().count('rawScale=1.5') > changes, 'No mixed-scale transition')
        capture('scaled')
        move()
        key(87)  # F11.
        wait(lambda: 'fullscreen=true' in text(), 'Fullscreen not acknowledged')
        capture('fullscreen')
        key(87)
        print('PASS mini player, mixed scale and fullscreen', flush=True)
        move(30, 30, 780, 830)
        audio_file = OUT / 'Dropped Native Audio.wav'
        audio_file.write_bytes(fixture.audio)
        source_log = (OUT / 'drag-source.log').open('w')
        source = subprocess.Popen(['/usr/bin/python3', str(ROOT / 'desktop-wayland/check/drag-source.py')],
                                  env=dict(env, OCTO_DROP_URI=audio_file.as_uri()), stdout=source_log, stderr=subprocess.STDOUT, start_new_session=True)
        wait(lambda: 'MAPPED' in (OUT / 'drag-source.log').read_text(), 'No native GTK source window')
        script('for (const w of workspace.windowList()) if (String(w.resourceClass).includes("drag-source")) {w.frameGeometry={x:850,y:80,width:300,height:160}; workspace.activeWindow=w;}')
        time.sleep(.4)
        input('move=990,155', 'button=272,1', *[f'move={x},155' for x in range(980,350,-30)], 'button=272,0')
        wait(lambda: 'Dropped Native Audio' in media('Metadata'), 'Native file drop did not start playing')
        wait(lambda: media('PlaybackStatus') == 'Playing', 'Dropped audio did not play')
        stop(source)
        source_log.close()
        print('PASS external GTK -> Wayland audio file drag/drop', flush=True)
        script('for (const w of workspace.windowList()) if (String(w.resourceClass) === "app.winters.octo.wayland") w.closeWindow();')
        assert process.wait(timeout=15) == 0
        assert 'clean-exit' in text()
        if os.environ.get('OCTO_CHECK_TRACE') == '1':
            trace = (OUT / 'trace.log').read_text()
            assert 'wayland-octo-player-test' in trace and '/dev/dri/renderD' in trace
            assert not any('connect(' in line and ('X11-unix' in line or 'htons(6000)' in line) for line in trace.splitlines())
            print('PASS independent strace: Wayland + DRM render node, no X11 connection', flush=True)
        print('PASS native application clean shutdown', flush=True)
    if os.environ.get('OCTO_CHECK_PORTAL') == '1':
        check_portals(env)
    print('PASS native acceptance complete', flush=True)
finally:
    (OUT / 'server-calls.json').write_text(json.dumps(fixture.calls, indent=2))
    fixture.close()
    stop(source)
    stop(process)
