struct Pair {
    first: i32,
    second: i32,
}

struct Quad {
    a: i32,
    b: i32,
    c: i32,
    d: i32,
}

fn sort_pair(x: i32, y: i32) -> Pair {
    if x <= y {
        return Pair { first: x, second: y };
    }
    return Pair { first: y, second: x };
}

fn merge_pairs(left: Pair, right: Pair) -> Quad {
    let mut first = 0;
    let mut second = 0;
    let mut third = 0;
    let mut fourth = 0;

    if left.first <= right.first {
        first = left.first;
        if left.second <= right.first {
            second = left.second;
            third = right.first;
            fourth = right.second;
        } else {
            second = right.first;
            if left.second <= right.second {
                third = left.second;
                fourth = right.second;
            } else {
                third = right.second;
                fourth = left.second;
            }
        }
    } else {
        first = right.first;
        if right.second <= left.first {
            second = right.second;
            third = left.first;
            fourth = left.second;
        } else {
            second = left.first;
            if right.second <= left.second {
                third = right.second;
                fourth = left.second;
            } else {
                third = left.second;
                fourth = right.second;
            }
        }
    }

    return Quad { a: first, b: second, c: third, d: fourth };
}

fn merge_sort4(a: i32, b: i32, c: i32, d: i32) -> Quad {
    let left = sort_pair(a, b);
    let right = sort_pair(c, d);
    return merge_pairs(left, right);
}

fn main() {
    let sorted = merge_sort4(9, 2, 7, 1);
    std::print("sorted:");
    std::print(sorted.a);
    std::print(sorted.b);
    std::print(sorted.c);
    std::print(sorted.d);
    return;
}
