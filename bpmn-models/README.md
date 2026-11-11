# BPMN models

| File | Type | Purpose |
|---|---|---|
| `condonation-main.bpmn` | Main process | End-to-end condonation workflow |
| `approval-child.bpmn` | Child process (Call Activity) | One approver's review — called as a sequential multi-instance |
| `notify-student-child.bpmn` | Child process (Call Activity) | Sends the result email to the student |
| `condonation-eligibility.dmn` | DMN decision | Eligibility slab, fee and required approvers |
| `forms/*.form` | Camunda Forms | Forms linked to the user tasks |

_Files are added in Parts 2 and 3._
