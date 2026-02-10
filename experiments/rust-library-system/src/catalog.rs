use crate::types::{ActionResult, LibraryState};

pub fn catalog_seed() -> LibraryState {
    return LibraryState {
        day: 1,
        available_a: 3,
        available_b: 2,
        available_c: 1,
        loans_m1: 0,
        loans_m2: 0,
        fines_m1: 0,
        fines_m2: 0,
    };
}

pub fn catalog_next_day(state: LibraryState) -> LibraryState {
    return LibraryState {
        day: state.day + 1,
        available_a: state.available_a,
        available_b: state.available_b,
        available_c: state.available_c,
        loans_m1: state.loans_m1,
        loans_m2: state.loans_m2,
        fines_m1: state.fines_m1,
        fines_m2: state.fines_m2,
    };
}

pub fn catalog_check_available(state: LibraryState, book_code: i32) -> ActionResult {
    let available = if book_code == 1 {
        state.available_a
    } else if book_code == 2 {
        state.available_b
    } else {
        state.available_c
    };

    if available > 0 {
        return ActionResult {
            state: state,
            ok: true,
            code: 0,
        };
    }

    return ActionResult {
        state: state,
        ok: false,
        code: 200 + book_code,
    };
}

pub fn catalog_take_copy(state: LibraryState, book_code: i32) -> LibraryState {
    let mut next_a = state.available_a;
    let mut next_b = state.available_b;
    let mut next_c = state.available_c;

    if book_code == 1 {
        next_a = state.available_a - 1;
    } else {
        if book_code == 2 {
            next_b = state.available_b - 1;
        } else {
            next_c = state.available_c - 1;
        }
    }

    return LibraryState {
        day: state.day,
        available_a: next_a,
        available_b: next_b,
        available_c: next_c,
        loans_m1: state.loans_m1,
        loans_m2: state.loans_m2,
        fines_m1: state.fines_m1,
        fines_m2: state.fines_m2,
    };
}

pub fn catalog_put_copy(state: LibraryState, book_code: i32) -> LibraryState {
    let mut next_a = state.available_a;
    let mut next_b = state.available_b;
    let mut next_c = state.available_c;

    if book_code == 1 {
        next_a = state.available_a + 1;
    } else {
        if book_code == 2 {
            next_b = state.available_b + 1;
        } else {
            next_c = state.available_c + 1;
        }
    }

    return LibraryState {
        day: state.day,
        available_a: next_a,
        available_b: next_b,
        available_c: next_c,
        loans_m1: state.loans_m1,
        loans_m2: state.loans_m2,
        fines_m1: state.fines_m1,
        fines_m2: state.fines_m2,
    };
}
