fn main() {
    let mut value = Option::Some(2);
    while let Option::Some(x) = value {
        std::print(x);
        value = Option::None;
    }
    return;
}
