use crate::types::{ActionResult, LibraryState};

pub fn members_check_eligibility(state: LibraryState, member_code: i32) -> ActionResult {
    let mut loans = state.loans_m2;
    let mut fines = state.fines_m2;
    let mut limit = 2;

    if member_code == 1 {
        loans = state.loans_m1;
        fines = state.fines_m1;
        limit = 3;
    }

    if loans >= limit {
        return ActionResult {
            state: state,
            ok: false,
            code: 300 + member_code,
        };
    }

    if fines > 2000 {
        return ActionResult {
            state: state,
            ok: false,
            code: 400 + member_code,
        };
    }

    return ActionResult {
        state: state,
        ok: true,
        code: 0,
    };
}

pub fn members_add_loan(state: LibraryState, member_code: i32) -> LibraryState {
    let mut next_m1 = state.loans_m1;
    let mut next_m2 = state.loans_m2;

    if member_code == 1 {
        next_m1 = state.loans_m1 + 1;
    } else {
        next_m2 = state.loans_m2 + 1;
    }

    return LibraryState {
        day: state.day,
        available_a: state.available_a,
        available_b: state.available_b,
        available_c: state.available_c,
        loans_m1: next_m1,
        loans_m2: next_m2,
        fines_m1: state.fines_m1,
        fines_m2: state.fines_m2,
    };
}

pub fn members_close_loan(state: LibraryState, member_code: i32) -> LibraryState {
    let mut next_m1 = state.loans_m1;
    let mut next_m2 = state.loans_m2;

    if member_code == 1 {
        if state.loans_m1 > 0 {
            next_m1 = state.loans_m1 - 1;
        }
    } else {
        if state.loans_m2 > 0 {
            next_m2 = state.loans_m2 - 1;
        }
    }

    return LibraryState {
        day: state.day,
        available_a: state.available_a,
        available_b: state.available_b,
        available_c: state.available_c,
        loans_m1: next_m1,
        loans_m2: next_m2,
        fines_m1: state.fines_m1,
        fines_m2: state.fines_m2,
    };
}

pub fn members_add_fine(state: LibraryState, member_code: i32, amount_cents: i32) -> LibraryState {
    let mut next_fine_m1 = state.fines_m1;
    let mut next_fine_m2 = state.fines_m2;

    if member_code == 1 {
        next_fine_m1 = state.fines_m1 + amount_cents;
    } else {
        next_fine_m2 = state.fines_m2 + amount_cents;
    }

    return LibraryState {
        day: state.day,
        available_a: state.available_a,
        available_b: state.available_b,
        available_c: state.available_c,
        loans_m1: state.loans_m1,
        loans_m2: state.loans_m2,
        fines_m1: next_fine_m1,
        fines_m2: next_fine_m2,
    };
}

pub fn members_pay(state: LibraryState, member_code: i32, payment_cents: i32) -> LibraryState {
    let mut next_fine_m1 = state.fines_m1;
    let mut next_fine_m2 = state.fines_m2;

    if member_code == 1 {
        if payment_cents >= state.fines_m1 {
            next_fine_m1 = 0;
        } else {
            next_fine_m1 = state.fines_m1 - payment_cents;
        }
    } else {
        if payment_cents >= state.fines_m2 {
            next_fine_m2 = 0;
        } else {
            next_fine_m2 = state.fines_m2 - payment_cents;
        }
    }

    return LibraryState {
        day: state.day,
        available_a: state.available_a,
        available_b: state.available_b,
        available_c: state.available_c,
        loans_m1: state.loans_m1,
        loans_m2: state.loans_m2,
        fines_m1: next_fine_m1,
        fines_m2: next_fine_m2,
    };
}
