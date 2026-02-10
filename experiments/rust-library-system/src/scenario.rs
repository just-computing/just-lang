use crate::catalog::{catalog_next_day, catalog_seed};
use crate::members::members_pay;
use crate::reports::{reports_print_action, reports_print_state};
use crate::types::LibraryState;

pub fn scenario_run() {
    let mut state = catalog_seed();
    state = reports_print_state(state);

    let checkout1 = crate::circulation::circulation_checkout(state, 1, 1);
    state = reports_print_action(checkout1);

    let checkout2 = crate::circulation::circulation_checkout(state, 2, 1);
    state = reports_print_action(checkout2);

    let checkout3 = crate::circulation::circulation_checkout(state, 2, 3);
    state = reports_print_action(checkout3);

    let mut i = 0;
    while i < 4 {
        state = catalog_next_day(state);
        i += 1;
    }

    let late_return = crate::circulation::circulation_return(state, 2, 1, 5);
    state = reports_print_action(late_return);

    state = members_pay(state, 2, 100);
    state = reports_print_state(state);

    let mut spin = 0;
    let extra_days = loop {
        if spin >= 2 {
            break spin;
        }
        spin += 1;
    };

    let mut day_bump = 0;
    while day_bump < extra_days {
        state = catalog_next_day(state);
        day_bump += 1;
    }

    let final_checkout = crate::circulation::circulation_checkout(state, 1, 2);
    state = reports_print_action(final_checkout);

    let _done: LibraryState = reports_print_state(state);
    return;
}
