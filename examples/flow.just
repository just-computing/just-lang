fn add(a: i32, b: i32) -> i32 {
    return a + b;
}

fn main() {
    let sum = add(2, 3);
    let label = if sum > 4 {
        let tag = "big";
        tag
    } else {
        "small"
    };
    std::print(sum);
    std::print(label);
    while true {
        std::print("loop");
        break;
    }
    return;
}
