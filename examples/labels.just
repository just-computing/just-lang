fn main() {
    'outer: for i in 0..3 {
        let mut j = 0;
        while j < 3 {
            j += 1;
            continue 'outer;
        }
    }
    std::print("done");
    return;
}
