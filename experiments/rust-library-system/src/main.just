mod entities;
mod errors;
mod reports;
mod system;

use entities::{Book, Genre, MemberTier};
use errors::LibraryError;
use system::LibraryManagementSystem;

fn seed_catalog(system: &mut LibraryManagementSystem) -> Result<(), LibraryError> {
    let books = vec![
        Book::new(
            "978-0131103627",
            "The C Programming Language",
            "Kernighan & Ritchie",
            Genre::Technology,
            3,
        ),
        Book::new(
            "978-0132350884",
            "Clean Code",
            "Robert C. Martin",
            Genre::Technology,
            4,
        ),
        Book::new(
            "978-0201633610",
            "Design Patterns",
            "Gamma, Helm, Johnson, Vlissides",
            Genre::Technology,
            2,
        ),
        Book::new(
            "978-0134494166",
            "Clean Architecture",
            "Robert C. Martin",
            Genre::Technology,
            2,
        ),
        Book::new(
            "978-0262033848",
            "Introduction to Algorithms",
            "Cormen et al.",
            Genre::Science,
            2,
        ),
        Book::new(
            "978-0140449136",
            "Meditations",
            "Marcus Aurelius",
            Genre::Philosophy,
            1,
        ),
        Book::new(
            "978-0679734529",
            "The Stranger",
            "Albert Camus",
            Genre::Fiction,
            2,
        ),
    ];

    for book in books {
        system.add_book(book)?;
    }

    Ok(())
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut library = LibraryManagementSystem::new();
    seed_catalog(&mut library)?;

    let alice = library.register_member("Alice Johnson", MemberTier::Premium);
    let bob = library.register_member("Bob Smith", MemberTier::Standard);
    let carol = library.register_member("Carol Lee", MemberTier::Student);

    let loan_1 = library.checkout_book(alice, "978-0131103627", 1)?;
    let _loan_2 = library.checkout_book(alice, "978-0132350884", 1)?;
    let loan_3 = library.checkout_book(bob, "978-0140449136", 2)?;
    let _loan_4 = library.checkout_book(carol, "978-0262033848", 2)?;

    let hold_position = library.place_hold(carol, "978-0140449136", 3)?;
    println!(
        "Carol joined hold queue for Meditations at position {}",
        hold_position
    );

    let late_days = library.return_book(loan_3, 25)?;
    println!("Bob returned Meditations with {} late day(s)", late_days);

    for notice in library.drain_notifications() {
        println!("notification: {}", notice);
    }

    let _loan_5 = library.checkout_book(carol, "978-0140449136", 26)?;
    let _loan_6 = library.checkout_book(bob, "978-0679734529", 26)?;

    let late_days_alice = library.return_book(loan_1, 45)?;
    println!(
        "Alice returned The C Programming Language with {} late day(s)",
        late_days_alice
    );

    let remaining_balance = library.pay_balance(alice, 500)?;
    println!(
        "Alice paid part of her fines. Remaining balance: ${:.2}",
        remaining_balance as f64 / 100.0
    );

    let report = library.operational_report(45);
    println!();
    println!("{}", report);

    Ok(())
}
