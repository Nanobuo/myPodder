// Niente finestra di console su Windows nelle build di release.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    mypodder_lib::run()
}
