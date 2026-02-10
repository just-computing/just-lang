use std::error::Error;
use std::fmt;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum LibraryError {
    BookAlreadyExists(String),
    BookNotFound(String),
    MemberNotFound(u32),
    LoanNotFound(u64),
    NoAvailableCopies(String),
    MemberLoanLimitReached(u32),
    MemberHasOutstandingBalance(u32),
    LoanAlreadyClosed(u64),
    DuplicateHoldRequest { member_id: u32, isbn: String },
    BookReservedForAnotherMember(String),
}

impl fmt::Display for LibraryError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            LibraryError::BookAlreadyExists(isbn) => {
                write!(f, "book with ISBN {} already exists", isbn)
            }
            LibraryError::BookNotFound(isbn) => write!(f, "book with ISBN {} not found", isbn),
            LibraryError::MemberNotFound(member_id) => {
                write!(f, "member {} not found", member_id)
            }
            LibraryError::LoanNotFound(loan_id) => write!(f, "loan {} not found", loan_id),
            LibraryError::NoAvailableCopies(isbn) => {
                write!(f, "no available copies for ISBN {}", isbn)
            }
            LibraryError::MemberLoanLimitReached(member_id) => {
                write!(f, "member {} reached loan limit", member_id)
            }
            LibraryError::MemberHasOutstandingBalance(member_id) => {
                write!(
                    f,
                    "member {} has outstanding balance above allowed threshold",
                    member_id
                )
            }
            LibraryError::LoanAlreadyClosed(loan_id) => {
                write!(f, "loan {} is already closed", loan_id)
            }
            LibraryError::DuplicateHoldRequest { member_id, isbn } => write!(
                f,
                "member {} already has an active hold request for ISBN {}",
                member_id, isbn
            ),
            LibraryError::BookReservedForAnotherMember(isbn) => write!(
                f,
                "book ISBN {} is reserved for a different member",
                isbn
            ),
        }
    }
}

impl Error for LibraryError {}
