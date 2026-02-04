fn main() {
    let value = 4;
    let label = match value {
        0 => "zero",
        1..=3 => "few",
        4 => "four",
        _ => "many",
    };
    std::print(label);
    return;
}
