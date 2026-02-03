fn main() {
    let value = 2;
    let label = match value {
        0 => "zero",
        1 => "one",
        2 => "two",
        _ => "many",
    };
    std::print(label);
    return;
}
