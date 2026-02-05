struct Point {
    x: i32,
    y: i32,
}

fn sum_to(n: i32) -> i32 {
    let mut acc = 0;
    let mut i = 0;
    while i < n {
        acc += i;
        i += 1;
    }
    return acc;
}

fn main() {
    let p = Point { x: 3, y: 4 };
    std::print(p.x + p.y);
    std::print(sum_to(5));
    return;
}
