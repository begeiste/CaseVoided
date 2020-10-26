//CASE_VOID
if (_disposition.case.caseType == "JC") {

    def underlyingCases = _disposition.case.findJoindedCaseNumbers('JCCP', true, null, null, null, false, null, null, '');
    underlyingCases.remove(_disposition.case.caseNumber);

    if (underlyingCases.size() > 0)
        addError("Case cannot be voided because it has underlying cases.");
    return;
}
// AW modification to the disposition type from 2100 to 1300SC 
// when the Small Claim case requires void case.IR546286
if (_disposition.dispositionType =='1300VSC' && _disposition.reopenReasonDate == null){
    cse = _disposition.case;
    //cse.dispositionType = '1300SC';
    cse.dispositionType = _disposition.dispositionType;
    cse.dispositionDate = _disposition.dispositionDate;

    for (subCase in cse.collect("subCases[dispositionDate == null]")) {
        // subCase.dispositionType = '1300SC';
        subCase.dispositionType = _disposition.dispositionType;
        subCase.dispositionDate = _disposition.dispositionDate;
    }
    cse.saveOrUpdate();
}
if (_disposition.dispositionType == '2100' && _disposition.reopenReasonDate == null) {
    cse = _disposition.case;
    cse.dispositionType = '2100';
    cse.dispositionDate = _disposition.dispositionDate;

    for (subCase in cse.collect("subCases[dispositionDate == null]")) {
        subCase.dispositionType = '2100';
        subCase.dispositionDate = _disposition.dispositionDate;
    }
    cse.saveOrUpdate();
}

supervisor = User.getCurrent().collect("authorities[authority == 'supervisor tasks' or authority == 'manager tasks']").notEmpty;
//logger.debug('supervisor: ' + supervisor)

if (!supervisor) {
    addError("You do not have supervisor acceess, please contact a supervisor to void the case.");
}


