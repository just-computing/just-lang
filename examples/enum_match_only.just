enum Maybe {
    Some(i32),
    None,
}

fn main() {
    let value = Maybe::Some(3);
    let label = match value {
        Maybe::Some(x) => x,
        Maybe::None => 0,
    };
    std::print(label);
    return;
}
