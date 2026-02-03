fn check() -> bool {
    std::print("check");
    return true;
}

fn pick() -> i32 {
    let value = if 1 < 2 { 10 } else { 20 };
    return value;
}

fn main() {
    let flag = false && check();
    std::print(flag);
    let result = pick();
    std::print(result);
    return;
}
