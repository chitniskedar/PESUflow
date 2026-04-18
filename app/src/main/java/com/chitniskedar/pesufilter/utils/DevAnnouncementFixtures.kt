package com.chitniskedar.pesufilter.utils

import com.chitniskedar.pesufilter.model.Announcement

object DevAnnouncementFixtures {

    fun sampleAnnouncements(): List<Announcement> {
        val dateRegex = Regex("""^\d{2}-[A-Za-z]+-\d{4}$""")
        val lines = RAW_SAMPLE
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        val announcements = mutableListOf<Announcement>()
        var index = lines.indexOfFirst { it.equals("Announcements", ignoreCase = true) }
        if (index >= 0) {
            index += 1
        } else {
            index = 0
        }

        while (index < lines.size) {
            val date = lines.getOrNull(index).orEmpty()
            if (!date.matches(dateRegex)) {
                index += 1
                continue
            }

            index += 1
            val block = mutableListOf<String>()
            while (index < lines.size && !lines[index].matches(dateRegex)) {
                block.add(lines[index])
                index += 1
            }

            val title = block.firstOrNull().orEmpty()
            if (title.isBlank()) {
                continue
            }

            val cleanedDetails = block
                .drop(1)
                .filterNot { it.equals(title, ignoreCase = true) }
                .filterNot { it.equals("Read more", ignoreCase = true) }
                .joinToString("\n")
                .ifBlank { title }

            announcements.add(
                Announcement(
                    title = title,
                    date = date,
                    fullText = buildString {
                        append(title)
                        if (cleanedDetails.isNotBlank()) {
                            append('\n')
                            append(cleanedDetails)
                        }
                    }
                )
            )
        }

        return announcements.distinctBy { it.stableId }
    }

    private val RAW_SAMPLE = """
Announcements
17-April-2026
B.Pharm 8th Sem - ISA 2 Theory Timetable
B.Pharm 8th Sem - ISA 2 Theory Timetable
B_Pharm_VIII_ISA_II_Theory_2026.pdf
Read more
17-April-2026
B.Pharm 4th, 6th & 8th Sem Lateral Entry - ISA 2 Theory Timetable
B.Pharm 4th, 6th & 8th Sem Lateral Entry - ISA 2 Theory Timetable
B_Pharm_IV_VI_&VIII_LE_2026.pdf
Read more
17-April-2026
B.Pharm 6th Sem - ISA 2 Theory Timetable
B.Pharm 6th Sem - ISA 2 Theory Timetable
B_Pharm_VI_ISA_II_Theory.pdf
Read more
17-April-2026
B.Pharm 4th Sem - ISA 2 Practical Timetable
B.Pharm 4th Sem - ISA 2 Practical Timetable
B_Pharm_IV_ISA_II_Practical_2026.pdf
Read more
17-April-2026
B.Pharm 6th Sem - ISA 2 Practical Timetable
B.Pharm 6th Sem - ISA 2 Practical Timetable
B_Pharm_VI_ISA_II_Practical_2026.pdf
Read more
17-April-2026
B.Pharm 4th Sem - ISA 2 Theory Timetable
B.Pharm 4th Sem - ISA 2 Theory Timetable
B_Pharm_IV_ISA_II_Theory_2026.pdf
Read more
17-April-2026
BBA & BBA-Analytics 4th sem - ISA 2 Timetable
BBA & BBA-Analytics 4th sem - ISA 2 Timetable
BBA_&_BBA-Analytics_4th_sem_-_ISA_2_Timetable.pdf
Read more
17-April-2026
BBA & BBA-Analytics 2nd sem - ISA 2 Timetable
BBA & BBA-Analytics 2nd sem - ISA 2 Timetable
2nd_Sem_BBA_&_BBA-Analytics_ISA-2_Timetable.pdf
Read more
16-April-2026
B.Tech ECE 8th Sem - ISA 2 Timetable - EC Campus
B.Tech ECE 8th Sem - ISA 2 Timetable - EC Campus
VIII_Sem_ISA2_TT_(2).pdf
Read more
16-April-2026
Executive M.Tech VLSI - Nov Cohort 2025 Batch - Sem 1 ISA 2 Timetable
Executive M.Tech VLSI - Nov Cohort 2025 Batch - Sem 1 ISA 2 Timetable
EMTech_In_VLSI__ISA2_TT.pdf
Read more
16-April-2026
M.Pharm 1st Sem - ISA 1 Practical Timetable
M.Pharm 1st Sem - ISA 1 Practical Timetable
M_Pharm_ISA1_Practical_1st_Sem_20260416141306.pdf
Read more
16-April-2026
Nursing 8th Sem - ISA 2 Theory and Practical Timetable
Nursing 8th Sem - ISA 2 Theory and Practical Timetable
bsc_nursing_8_th_sem_isa2_tt.pdf
Read more
16-April-2026
Nursing 6th Sem - ISA 2 Theory and Practical Timetable
Nursing 6th Sem - ISA 2 Theory and Practical Timetable
bsc_nursing_6th_sem_isa2_tt.pdf
Read more
16-April-2026
Nursing 4th Sem - ISA 2 Theory and Practical Timetable
Nursing 4th Sem - ISA 2 Theory and Practical Timetable
bsc_nursing_4_th_sem_isa2_tt.pdf
Read more
16-April-2026
Nursing 3rd, 5th and 7th Sem - ISA 2 Retest Timetable
Nursing 3rd, 5th and 7th Sem - ISA 2 Retest Timetable
bsc_nursing_3rd_5th__7_th_sem_isa2_tt.pdf
Read more
16-April-2026
ISA 2 Time Table - B Com, B Com EVNG, ACCA, and CMA
ISA 2 Time Table - B Com, B Com EVNG, ACCA, and CMA
ISA_2_tt.pdf
Read more
15-April-2026
B.Tech ECE 4th and 6th Sem ISA 2 Timetable - EC Campus
B.Tech ECE 4th and 6th Sem ISA 2 Timetable - EC Campus
ece__isa2_tt_4_&_6_sem.pdf
Read more
15-April-2026
B Tech CSE 4th & 6th ISA 2 Time table
B Tech CSE 4th & 6th ISA 2 Time table
B_Tech_CSE_ISA_2.pdf
Read more
14-April-2026
B.Tech (Lateral Entry) in Mechanical Engineering for HCL Tech - ISA 1 Timetable
B.Tech (Lateral Entry) in Mechanical Engineering for HCL Tech - ISA 1 Timetable
3rd_sem_lateral_entry_hcl_tech__tt.pdf
Read more
13-April-2026
Summer Internship sponsored - Gauntlet
Dear Students,
We are seeking highly motivated students currently in 6th Sem for a specialized 2-month internship
focused on Linux kernel observability and security. You will research the inner workings of
eBPF, develop programs to intercept system activities (such as syscalls and network traffic),
and build a pipeline to export this telemetry. The final goal is a functional POC that collects
real-time data from a Linux host and streams it to our Gauntlet server for analysis. This is a
deep-dive role perfect for those passionate about systems programming and low-level Linux
internals.
Required Skills & Academic Experience
Systems Programming: Strong academic experience in C or C++; familiarity with memory management and pointers is essential.
Operating Systems (OS): High marks in OS coursework with a solid understanding of User/Kernel space separation, interrupts, and system calls.
Linux Proficiency: Comfort with the Linux command line and experience writing shell scripts or managing Linux-based environments (Ubuntu/Fedora).
2. Preferred Project Experience
Kernel/Systems Projects: Any experience with socket programming, multi-threading, or basic kernel module experimentation.
Data Handling: Experience using Python or Go for academic projects involving JSON serialization and REST API communication.
If interested, please fill the google form: https://forms.gle/nkUWBqPg6osuEfcH6
Interviews will be conducted later this week and shortlisted candidates will be contacted over email.
Read more
10-April-2026
Summer Internship - Course development
We are building a couple of new elective courses for 3rd year students from the CSE, AIML, and ECE departments. Course names: "Platform and Systems Engineering" and "RTOS and Embedded Systems". This internship is about building the course material and experiential learning part of the courses. It will be a fully hands-on internship where you will work deeply with Linux, QEMU, low-level programming, ARM M4 board, various RTOS concepts, etc., for the development of various labs. Only for students who are completing the 6th Semester from the CSE, AIML, or ECE department. Internship will be in the RR campus.
Pre-requisites: Very good and hands-on with Linux OS. Familiarity with QEMU is a plus. Understanding of low-level programming is a plus. 6th Semester students only.
If interested, please fill out the form: https://forms.gle/WQmfyDbujBNNp63S8
Read more
09-April-2026
Summer Internship sponsored - FPGA
This summer internship will be sponsored by an industry partner and will be a paid internship.
This is open to all 2nd and 3rd year students from ECE, CSE and AIML departments from both RR and EC campuses
All students need to work out of the RR campus ISFCR lab full time.
The following are examples of the projects:
- PQC cryptographic algorithm implementations on FPGA
- QUIC protocol implementation on FPGA
Primary requirements: Verilog and C development
If interested, please fill out the Google form: https://forms.gle/Swx28AiiJXYkt86s6
Interviews will be conducted next week, and shortlisted candidates will be contacted via email.
Read more
07-April-2026
Pharm.D - 2nd, 3rd, 4th and 5th Year - ISA 3 Theory Timetable
Pharm.D - 2nd, 3rd, 4th and 5th Year - ISA 3 Theory Timetable
pharm_d_2nd_year_theory_TT.pdf
pharm_d_3rd_year_theory_TT.pdf
pharm_d_4th_year_theory_TT.pdf
pharm_d_5_th_year_theory_TT.pdf
Read more
07-April-2026
Pharm.D - 2nd, 3rd, 4th and 5th Year - ISA 3 Practical Timetable
Pharm.D - 2nd, 3rd, 4th and 5th Year - ISA 3 Practical Timetable
pharm_d_2nd_year_pra_TT.pdf
pharm_d_3rd_year_pra_TT.pdf
pharm_d_4th_year_pra_TT.pdf
pharm_d_5_th_year_pra_TT.pdf
Read more
02-April-2026
M.Pharm Sem 1 - ISA 1 Theory Timetable
M.Pharm Sem 1 - ISA 1 Theory Timetable
isa1_mpharm_tt.pdf
Read more
31-March-2026
ESA Nursing 1st Sem to 7th Sem - ESA June - Regular & Backlog Draft Timetable
ESA Nursing 1st Sem to 7th Sem - ESA June - Regular & Backlog Draft Timetable
ESA_June_2026_DRAFT_TIME_TABLE__NURSING.pdf
Read more
25-March-2026
ESA JUNE 2026_DRAFT_TIME TABLE_ 1stB PHARM
ESA JUNE 2026_DRAFT_TIME TABLE_ 1stB PHARM
ESA_JUNE_2026_DRAFT_TIME_TABLE__1stB_PHARM.pdf
Read more
25-March-2026
ESA JUNE 2026_DRAFT_TIME TABLE_1stPHARM D
ESA JUNE 2026_DRAFT_TIME TABLE_1stPHARM D
ESA_JUNE_2026_DRAFT_TIME_TABLE_1stPHARM_D.pdf
Read more
24-March-2026
ESA JUNE 2026_DRAFT_TIME TABLE_2025 Batch BTech
ESA JUNE 2026_DRAFT_TIME TABLE_2025 Batch BTech
ESA_JUNE_2026_DRAFT_TIME_TABLE_BTECH_S_&_H.pdf
Read more
24-March-2026
Mtech VLSI - Cohort 1 - Sem 1 - ESA Timetable
Mtech VLSI - Cohort 1 - Sem 1 - ESA Timetable
M_Tech-VLSI_November_ESA_timetable.pdf
Read more
20-March-2026
ESA MAY 2026_DRAFT_TIME TABLE_BCOM, ACCA & CMA
ESA MAY 2026_DRAFT_TIME TABLE_BCOM, ACCA & CMA
ESA_MAY_2026_DRAFT_TIME_TABLE_BCOM__ACCA_&_CMA.pdf
Read more
20-March-2026
ESA MAY 2026_DRAFT_TIME TABLE_PHARM D
ESA MAY 2026_DRAFT_TIME TABLE_PHARM D
ESA_MAY_2026_DRAFT_TIME_TABLE_PHARM_D.pdf
Read more
20-March-2026
ESA MAY 2026_DRAFT_TIME TABLE_B PHARM
ESA MAY 2026_DRAFT_TIME TABLE_B PHARM
ESA_MAY_2026_DRAFT_TIME_TABLE_B_PHARM.pdf
Read more
20-March-2026
ESA MAY 2026_DRAFT_TIME TABLE_BBA & BBA BA
ESA MAY 2026_DRAFT_TIME TABLE_BBA & BBA BA
ESA_MAY_2026_DRAFT_TIME_TABLE_BBA_&_BBA_BA.pdf
Read more
20-March-2026
ESA MAY 2026_DRAFT_TIME TABLE_BTECH
ESA MAY 2026_DRAFT_TIME TABLE_BTECH
ESA_MAY_2026_DRAFT_TIME_TABLE_BTECH.pdf
Read more
20-March-2026
May-2026 ESA Backlog enrollment notification
May-2026 ESA Backlog enrollment notification
Backlog_EnrollmentNotification_May2026.pdf
Read more
17-March-2026
MBBS Phase 1 and Phase 2 - ISA 2 Timetable
MBBS Phase 1 and Phase 2 - ISA 2 Timetable
MBBS_PHASE_II_ISA_2_TIME_TABLE.pdf
MBBS_PHASE_I_ISA_2_TIME_TABLE.pdf
Read more
02-March-2026
Calendar of Events for Semester II of the B.Sc. Nursing program
Dear Student/Parent,
Greetings from the Department of B.Sc. Nursing, PES University!
Please find attached the Calendar of Events for Semester II of the B.Sc. Nursing program, covering the period from April to September 2026.
Warm regards,
PESU Institute of Nursing
EC Campus
II__Semester_-_Calendar_of_Events-_PESU_Institute_of_Nursing___(1).pdf
""".trimIndent()
}
