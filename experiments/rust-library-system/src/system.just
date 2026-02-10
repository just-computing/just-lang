use crate::entities::{Book, HoldRequest, LoanRecord, Member, MemberTier};
use crate::errors::LibraryError;
use crate::reports::{
    InventoryLine, MemberBalanceLine, OperationalReport, OverdueLine,
};
use std::collections::{HashMap, VecDeque};

#[derive(Debug)]
pub struct LibraryManagementSystem {
    books: HashMap<String, Book>,
    members: HashMap<u32, Member>,
    loans: HashMap<u64, LoanRecord>,
    holds: HashMap<String, VecDeque<HoldRequest>>,
    notifications: Vec<String>,
    next_member_id: u32,
    next_loan_id: u64,
    late_fee_per_day_cents: u32,
    max_allowed_balance_cents: u32,
}

impl LibraryManagementSystem {
    pub fn new() -> Self {
        Self {
            books: HashMap::new(),
            members: HashMap::new(),
            loans: HashMap::new(),
            holds: HashMap::new(),
            notifications: Vec::new(),
            next_member_id: 1,
            next_loan_id: 10_000,
            late_fee_per_day_cents: 75,
            max_allowed_balance_cents: 2_000,
        }
    }

    pub fn add_book(&mut self, book: Book) -> Result<(), LibraryError> {
        if self.books.contains_key(&book.isbn) {
            return Err(LibraryError::BookAlreadyExists(book.isbn));
        }
        self.books.insert(book.isbn.clone(), book);
        Ok(())
    }

    pub fn register_member(&mut self, name: &str, tier: MemberTier) -> u32 {
        let id = self.next_member_id;
        self.next_member_id += 1;
        self.members.insert(id, Member::new(id, name, tier));
        id
    }

    pub fn checkout_book(
        &mut self,
        member_id: u32,
        isbn: &str,
        current_day: u32,
    ) -> Result<u64, LibraryError> {
        let member = self
            .members
            .get(&member_id)
            .ok_or(LibraryError::MemberNotFound(member_id))?;

        if member.balance_cents > self.max_allowed_balance_cents {
            return Err(LibraryError::MemberHasOutstandingBalance(member_id));
        }

        if !member.can_borrow_more() {
            return Err(LibraryError::MemberLoanLimitReached(member_id));
        }

        if let Some(queue) = self.holds.get(isbn) {
            if let Some(front) = queue.front() {
                if front.member_id != member_id {
                    return Err(LibraryError::BookReservedForAnotherMember(isbn.to_string()));
                }
            }
        }

        let due_day = current_day + member.tier.loan_period_days();

        {
            let book = self
                .books
                .get_mut(isbn)
                .ok_or(LibraryError::BookNotFound(isbn.to_string()))?;
            book.checkout_one()?;
        }

        let loan_id = self.next_loan_id;
        self.next_loan_id += 1;

        let loan = LoanRecord::new(loan_id, member_id, isbn, current_day, due_day);
        self.loans.insert(loan_id, loan);

        if let Some(member_mut) = self.members.get_mut(&member_id) {
            member_mut.add_loan(loan_id);
        }

        if let Some(queue) = self.holds.get_mut(isbn) {
            if let Some(front) = queue.front() {
                if front.member_id == member_id {
                    queue.pop_front();
                }
            }
        }

        Ok(loan_id)
    }

    pub fn place_hold(
        &mut self,
        member_id: u32,
        isbn: &str,
        current_day: u32,
    ) -> Result<usize, LibraryError> {
        if !self.members.contains_key(&member_id) {
            return Err(LibraryError::MemberNotFound(member_id));
        }

        if !self.books.contains_key(isbn) {
            return Err(LibraryError::BookNotFound(isbn.to_string()));
        }

        let queue = self.holds.entry(isbn.to_string()).or_default();

        if queue.iter().any(|hold| hold.member_id == member_id) {
            return Err(LibraryError::DuplicateHoldRequest {
                member_id,
                isbn: isbn.to_string(),
            });
        }

        queue.push_back(HoldRequest {
            member_id,
            requested_day: current_day,
        });

        Ok(queue.len())
    }

    pub fn return_book(&mut self, loan_id: u64, current_day: u32) -> Result<u32, LibraryError> {
        let (member_id, isbn, late_days) = {
            let loan = self
                .loans
                .get_mut(&loan_id)
                .ok_or(LibraryError::LoanNotFound(loan_id))?;

            let member_id = loan.member_id;
            let isbn = loan.isbn.clone();
            let late_days = loan.mark_returned(current_day)?;
            (member_id, isbn, late_days)
        };

        {
            let book = self
                .books
                .get_mut(&isbn)
                .ok_or(LibraryError::BookNotFound(isbn.clone()))?;
            book.return_one();
        }

        {
            let member = self
                .members
                .get_mut(&member_id)
                .ok_or(LibraryError::MemberNotFound(member_id))?;
            member.close_loan(loan_id);
            if late_days > 0 {
                member.add_penalty(late_days * self.late_fee_per_day_cents);
            }
        }

        self.notify_next_hold_candidate(&isbn);
        Ok(late_days)
    }

    pub fn pay_balance(
        &mut self,
        member_id: u32,
        amount_cents: u32,
    ) -> Result<u32, LibraryError> {
        let member = self
            .members
            .get_mut(&member_id)
            .ok_or(LibraryError::MemberNotFound(member_id))?;
        member.pay(amount_cents);
        Ok(member.balance_cents)
    }

    pub fn drain_notifications(&mut self) -> Vec<String> {
        let mut out = Vec::new();
        std::mem::swap(&mut self.notifications, &mut out);
        out
    }

    pub fn operational_report(&self, current_day: u32) -> OperationalReport {
        let mut inventory: Vec<InventoryLine> = self
            .books
            .values()
            .map(|book| InventoryLine {
                isbn: book.isbn.clone(),
                title: book.title.clone(),
                author: book.author.clone(),
                genre: book.genre.clone(),
                total_copies: book.total_copies,
                available_copies: book.available_copies,
            })
            .collect();
        inventory.sort_by(|a, b| a.title.cmp(&b.title).then(a.isbn.cmp(&b.isbn)));

        let mut overdue: Vec<OverdueLine> = self
            .loans
            .values()
            .filter(|loan| loan.is_overdue(current_day))
            .map(|loan| OverdueLine {
                loan_id: loan.id,
                member_id: loan.member_id,
                isbn: loan.isbn.clone(),
                days_overdue: current_day - loan.due_day,
            })
            .collect();
        overdue.sort_by(|a, b| {
            b.days_overdue
                .cmp(&a.days_overdue)
                .then(a.loan_id.cmp(&b.loan_id))
        });

        let mut balances: Vec<MemberBalanceLine> = self
            .members
            .values()
            .map(|member| MemberBalanceLine {
                member_id: member.id,
                member_name: member.name.clone(),
                balance_cents: member.balance_cents,
                active_loans: member.active_loans.len(),
            })
            .collect();
        balances.sort_by(|a, b| {
            b.balance_cents
                .cmp(&a.balance_cents)
                .then(a.member_id.cmp(&b.member_id))
        });

        let mut holds_by_isbn: Vec<(String, usize)> = self
            .holds
            .iter()
            .filter_map(|(isbn, queue)| {
                if queue.is_empty() {
                    None
                } else {
                    Some((isbn.clone(), queue.len()))
                }
            })
            .collect();
        holds_by_isbn.sort_by(|a, b| a.0.cmp(&b.0));

        OperationalReport {
            day: current_day,
            inventory,
            overdue,
            balances,
            holds_by_isbn,
        }
    }

    fn notify_next_hold_candidate(&mut self, isbn: &str) {
        let available = self
            .books
            .get(isbn)
            .map(|book| book.available_copies)
            .unwrap_or(0);

        if available == 0 {
            return;
        }

        let next_member = self
            .holds
            .get(isbn)
            .and_then(|queue| queue.front())
            .map(|hold| hold.member_id);

        if let Some(member_id) = next_member {
            self.notifications.push(format!(
                "member {} can now checkout reserved book ISBN {}",
                member_id, isbn
            ));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::LibraryManagementSystem;
    use crate::entities::{Book, Genre, MemberTier};

    #[test]
    fn standard_member_cannot_exceed_limit() {
        let mut system = LibraryManagementSystem::new();
        for idx in 0..4 {
            let isbn = format!("isbn-{}", idx);
            system
                .add_book(Book::new(
                    &isbn,
                    &format!("Book {}", idx),
                    "Test Author",
                    Genre::Technology,
                    1,
                ))
                .expect("book insertion should succeed");
        }

        let member = system.register_member("Taylor", MemberTier::Standard);

        assert!(system.checkout_book(member, "isbn-0", 1).is_ok());
        assert!(system.checkout_book(member, "isbn-1", 1).is_ok());
        assert!(system.checkout_book(member, "isbn-2", 1).is_ok());

        let fourth = system.checkout_book(member, "isbn-3", 1);
        assert!(fourth.is_err());
    }

    #[test]
    fn late_return_generates_penalty() {
        let mut system = LibraryManagementSystem::new();
        system
            .add_book(Book::new(
                "isbn-late",
                "Compilers in Practice",
                "A. Engineer",
                Genre::Science,
                1,
            ))
            .expect("book insertion should succeed");

        let member = system.register_member("Morgan", MemberTier::Student);
        let loan = system
            .checkout_book(member, "isbn-late", 10)
            .expect("loan should be created");

        let late_days = system
            .return_book(loan, 40)
            .expect("return should be accepted");
        assert_eq!(late_days, 9);

        let report = system.operational_report(40);
        let balance = report
            .balances
            .into_iter()
            .find(|line| line.member_id == member)
            .expect("member should be present");

        assert_eq!(balance.balance_cents, 9 * 75);
    }
}
