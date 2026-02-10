mod types;
mod catalog;
mod members;
mod circulation;
mod reports;
mod scenario;

use scenario::scenario_run;

fn main() {
    scenario_run();
    return;
}
