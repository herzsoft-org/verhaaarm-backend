package moe.herz.verhaarmbackend.period;

public enum PeriodType {
	/** A normal Conventsperiode ending on a Convent or Abconvent. */
	CONVENT,
	/** The Conventsperiode ending on an Anconvent - covers the preceding Semesterferien (rule 7). */
	SEMESTER_BREAK,
	/** The still-running period after the most recent Convent, while a semester is open (no Abconvent yet). */
	OPEN,
	/** The still-running Semesterferien after an Abconvent, before the next Anconvent has been scheduled (rule 6). */
	OPEN_SEMESTER_BREAK
}
