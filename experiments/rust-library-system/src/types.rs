pub struct LibraryState {
    pub day: i32,
    pub available_a: i32,
    pub available_b: i32,
    pub available_c: i32,
    pub loans_m1: i32,
    pub loans_m2: i32,
    pub fines_m1: i32,
    pub fines_m2: i32,
}

pub struct ActionResult {
    pub state: LibraryState,
    pub ok: bool,
    pub code: i32,
}
