use crate::catalog::{catalog_check_available, catalog_put_copy, catalog_take_copy};
use crate::members::{
    members_add_fine,
    members_add_loan,
    members_check_eligibility,
    members_close_loan,
};
use crate::types::{ActionResult, LibraryState};

pub fn circulation_checkout(state: LibraryState, member_code: i32, book_code: i32) -> ActionResult {
    let eligibility = members_check_eligibility(state, member_code);
    if !eligibility.ok {
        return ActionResult {
            state: eligibility.state,
            ok: false,
            code: eligibility.code,
        };
    }

    let availability = catalog_check_available(eligibility.state, book_code);
    if !availability.ok {
        return ActionResult {
            state: availability.state,
            ok: false,
            code: availability.code,
        };
    }

    let after_take = catalog_take_copy(availability.state, book_code);
    let next_state = members_add_loan(after_take, member_code);

    return ActionResult {
        state: next_state,
        ok: true,
        code: 0,
    };
}

pub fn circulation_return(state: LibraryState, member_code: i32, book_code: i32, late_days: i32) -> ActionResult {
    let after_put = catalog_put_copy(state, book_code);
    let after_close = members_close_loan(after_put, member_code);

    let final_state = if late_days > 0 {
        members_add_fine(after_close, member_code, late_days * 75)
    } else {
        after_close
    };

    return ActionResult {
        state: final_state,
        ok: true,
        code: 1000 + late_days,
    };
}
