fn main() {
    let value = Option::Some(3);
    if let Option::Some(x) = value {
        std::print(x);
    } else {
        std::print(0);
    }
    return;
}
