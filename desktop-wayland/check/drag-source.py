#!/usr/bin/python3
"""Independent GTK4 source for a real Wayland URI-list drag into the player."""
import os
import gi
gi.require_version('Gtk', '4.0')
from gi.repository import Gtk, Gdk, GLib
app = Gtk.Application(application_id='app.winters.octo.drag-source')


def activate(app):
    window = Gtk.ApplicationWindow(application=app, title='Octo DnD test source')
    window.set_default_size(300, 160)
    label = Gtk.Label(label='Drag the audio file to Octo')
    drag = Gtk.DragSource()
    drag.set_actions(Gdk.DragAction.COPY)
    drag.connect('prepare', lambda *args: Gdk.ContentProvider.new_for_bytes(
        'text/uri-list', GLib.Bytes.new((os.environ['OCTO_DROP_URI'] + '\r\n').encode())))
    label.add_controller(drag)
    window.set_child(label)
    window.connect('map', lambda *args: print('MAPPED', flush=True))
    window.present()


app.connect('activate', activate)
app.run()
