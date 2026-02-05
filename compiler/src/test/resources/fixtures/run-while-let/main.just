fn main() {
    let mut current: Option<i32> = Option::Some(2);
    while let Option::Some(v) = current {
        if v == 2 {
            std::print(v);
            current = Option::None;
        } else {
            std::print(0);
            current = Option::None;
        }
    }
    return;
}
