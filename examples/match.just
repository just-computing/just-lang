fn main() {
    let x = 3;
    let label = match x {
        0 => "zero",
        1..=2 => "small",
        3 => "three",
        _ => "many",
    };
    std::print(label);
    return;
}
