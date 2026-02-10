use crate::errors::LibraryError;
use std::fmt;

#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord)]
pub enum Genre {
    Fiction,
    NonFiction,
    Science,
    Technology,
    History,
    Philosophy,
}

impl fmt::Display for Genre {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let value = match self {
            Genre::Fiction => "Fiction",
            Genre::NonFiction => "Non-Fiction",
            Genre::Science => "Science",
            Genre::Technology => "Technology",
            Genre::History => "History",
            Genre::Philosophy => "Philosophy",
        };
        write!(f, "{}", value)
    }
}

#[derive(Debug, Clone)]
pub struct Book {
    pub isbn: String,
    pub title: String,
    pub author: String,
    pub genre: Genre,
    pub total_copies: u32,
    pub available_copies: u32,
}

impl Book {
    pub fn new(isbn: &str, title: &str, author: &str, genre: Genre, total_copies: u32) -> Self {
        Self {
            isbn: isbn.to_string(),
            title: title.to_string(),
            author: author.to_string(),
            genre,
            total_copies,
            available_copies: total_copies,
        }
    }

    pub fn has_available_copy(&self) -> bool {
        self.available_copies > 0
    }

    pub fn checkout_one(&mut self) -> Result<(), LibraryError> {
        if self.available_copies == 0 {
            return Err(LibraryError::NoAvailableCopies(self.isbn.clone()));
        }
        self.available_copies -= 1;
        Ok(())
    }

    pub fn return_one(&mut self) {
        self.available_copies += 1;
        if self.available_copies > self.total_copies {
            self.available_copies = self.total_copies;
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MemberTier {
    Standard,
    Premium,
    Student,
}

impl MemberTier {
    pub fn max_loan_limit(&self) -> usize {
        match self {
            MemberTier::Standard => 3,
            MemberTier::Premium => 7,
            MemberTier::Student => 5,
        }
    }

    pub fn loan_period_days(&self) -> u32 {
        match self {
            MemberTier::Standard => 14,
            MemberTier::Premium => 30,
            MemberTier::Student => 21,
        }
    }
}

impl fmt::Display for MemberTier {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let value = match self {
            MemberTier::Standard => "Standard",
            MemberTier::Premium => "Premium",
            MemberTier::Student => "Student",
        };
        write!(f, "{}", value)
    }
}

#[derive(Debug, Clone)]
pub struct Member {
    pub id: u32,
    pub name: String,
    pub tier: MemberTier,
    pub active_loans: Vec<u64>,
    pub balance_cents: u32,
}

impl Member {
    pub fn new(id: u32, name: &str, tier: MemberTier) -> Self {
        Self {
            id,
            name: name.to_string(),
            tier,
            active_loans: Vec::new(),
            balance_cents: 0,
        }
    }

    pub fn can_borrow_more(&self) -> bool {
        self.active_loans.len() < self.tier.max_loan_limit()
    }

    pub fn add_loan(&mut self, loan_id: u64) {
        self.active_loans.push(loan_id);
    }

    pub fn close_loan(&mut self, loan_id: u64) {
        self.active_loans.retain(|current| *current != loan_id);
    }

    pub fn add_penalty(&mut self, amount_cents: u32) {
        self.balance_cents += amount_cents;
    }

    pub fn pay(&mut self, amount_cents: u32) {
        self.balance_cents = self.balance_cents.saturating_sub(amount_cents);
    }
}

#[derive(Debug, Clone)]
pub struct LoanRecord {
    pub id: u64,
    pub member_id: u32,
    pub isbn: String,
    pub issued_day: u32,
    pub due_day: u32,
    pub returned_day: Option<u32>,
}

impl LoanRecord {
    pub fn new(id: u64, member_id: u32, isbn: &str, issued_day: u32, due_day: u32) -> Self {
        Self {
            id,
            member_id,
            isbn: isbn.to_string(),
            issued_day,
            due_day,
            returned_day: None,
        }
    }

    pub fn mark_returned(&mut self, current_day: u32) -> Result<u32, LibraryError> {
        if self.returned_day.is_some() {
            return Err(LibraryError::LoanAlreadyClosed(self.id));
        }
        self.returned_day = Some(current_day);
        if current_day > self.due_day {
            Ok(current_day - self.due_day)
        } else {
            Ok(0)
        }
    }

    pub fn is_overdue(&self, current_day: u32) -> bool {
        self.returned_day.is_none() && current_day > self.due_day
    }
}

#[derive(Debug, Clone)]
pub struct HoldRequest {
    pub member_id: u32,
    pub requested_day: u32,
}
