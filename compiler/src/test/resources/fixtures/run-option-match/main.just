fn unwrap_or_zero(x: Option<i32>) -> i32 {
    let out = match x {
        Option::Some(v) => v,
        Option::None => 0,
    };
    return out;
}

fn main() {
    let a: Option<i32> = Option::Some(7);
    let b: Option<i32> = Option::None;
    std::print(unwrap_or_zero(a));
    std::print(unwrap_or_zero(b));
    return;
}
