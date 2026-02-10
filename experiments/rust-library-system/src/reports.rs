use crate::types::{ActionResult, LibraryState};

pub fn reports_print_action(action: ActionResult) -> LibraryState {
    println!("action_ok");
    println!("{}", action.ok);
    println!("action_code");
    println!("{}", action.code);
    return action.state;
}

pub fn reports_print_state(state: LibraryState) -> LibraryState {
    println!("day");
    println!("{}", state.day);
    println!("available_a");
    println!("{}", state.available_a);
    println!("available_b");
    println!("{}", state.available_b);
    println!("available_c");
    println!("{}", state.available_c);
    println!("loans_m1");
    println!("{}", state.loans_m1);
    println!("loans_m2");
    println!("{}", state.loans_m2);
    println!("fines_m1");
    println!("{}", state.fines_m1);
    println!("fines_m2");
    println!("{}", state.fines_m2);
    return state;
}
