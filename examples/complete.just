struct User {
    id: i32,
    active: bool,
}

fn main() {
    let base = 7;
    let score = (base + 3) * 2;
    let is_even = (score / 2) * 2 == score;
    let flag = is_even && true;
    let neg = -base;
    let user = User { id: score, active: flag };
    std::print(score);
    std::print(is_even);
    std::print(flag || false);
    std::print(user);
    std::print(user.id);
    std::print(neg);
}
