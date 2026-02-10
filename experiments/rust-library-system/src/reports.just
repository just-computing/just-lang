use crate::entities::Genre;
use std::fmt;

#[derive(Debug, Clone)]
pub struct InventoryLine {
    pub isbn: String,
    pub title: String,
    pub author: String,
    pub genre: Genre,
    pub total_copies: u32,
    pub available_copies: u32,
}

#[derive(Debug, Clone)]
pub struct OverdueLine {
    pub loan_id: u64,
    pub member_id: u32,
    pub isbn: String,
    pub days_overdue: u32,
}

#[derive(Debug, Clone)]
pub struct MemberBalanceLine {
    pub member_id: u32,
    pub member_name: String,
    pub balance_cents: u32,
    pub active_loans: usize,
}

#[derive(Debug, Clone)]
pub struct OperationalReport {
    pub day: u32,
    pub inventory: Vec<InventoryLine>,
    pub overdue: Vec<OverdueLine>,
    pub balances: Vec<MemberBalanceLine>,
    pub holds_by_isbn: Vec<(String, usize)>,
}

impl fmt::Display for OperationalReport {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        writeln!(f, "=== Library Operational Report (day {}) ===", self.day)?;
        writeln!(f, "Inventory:")?;
        for line in &self.inventory {
            writeln!(
                f,
                "  [{}] {} by {} ({}) - {}/{} available",
                line.isbn,
                line.title,
                line.author,
                line.genre,
                line.available_copies,
                line.total_copies
            )?;
        }

        writeln!(f, "Overdue loans: {}", self.overdue.len())?;
        for loan in &self.overdue {
            writeln!(
                f,
                "  Loan {} | member {} | ISBN {} | {} day(s) overdue",
                loan.loan_id, loan.member_id, loan.isbn, loan.days_overdue
            )?;
        }

        writeln!(f, "Outstanding balances:")?;
        for member in &self.balances {
            writeln!(
                f,
                "  Member {} ({}) - ${:.2} with {} active loan(s)",
                member.member_id,
                member.member_name,
                member.balance_cents as f64 / 100.0,
                member.active_loans
            )?;
        }

        writeln!(f, "Hold queues:")?;
        for (isbn, queued) in &self.holds_by_isbn {
            writeln!(f, "  ISBN {} -> {} queued", isbn, queued)?;
        }

        Ok(())
    }
}
