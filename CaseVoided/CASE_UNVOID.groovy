//CASE_UNVOID
logger.debug("unvoid case ${_case}");

//voided = _case.dispositionType == '2100' || _case.collect("roaMessages[message=='Case is Voided']").notEmpty;
//IR546286 Charlton added on 9/8/2020
voided = (_case.dispositionType == '2100' || _case.collect("roaMessages[message=='Case is Voided']")).notEmpty;
if (!voided) {
  addError("Case ${_case.caseNumber} is currently not void");
  return;
}
if (_caseDispo && !_caseDispoDate) {
  //addWarning("Case Disposition Type given but no Disposition Date");
  //return;
}
if (!_caseDispo && _caseDispoDate) {
  //addWarning("Case Disposition Date given but no Disposition Type");
  //return;
}

//for (disposition in _case.collect("dispositions[dispositionType=='2100']")) {
//IR546286 Charlton added on 9/8/2020
for (disposition in _case.collect("dispositions[dispositionType=='2100']")) {
  logger.debug("delete case void dispo ${disposition}");
  _case.dispositions.remove(disposition);
  disposition.case = null;
  disposition.delete();
}

//for (subCase in _case.collect("subCases[dispositionType=='2100']")) {
//IR546286 Charlton added on 9/8/2020
for (subCase in _case.collect("subCases[dispositionType=='2100']")) {
  logger.debug("clear subcase dispo ${subCase}");
  subCase.dispositionType = _caseDispo;
  subCase.dispositionDate = _caseDispoDate;
  subCase.saveOrUpdate();
}


def roaToRemove = IdUtil.toIdCollection(StringUtils.splitToCollection(_roaIdsToRemove,','))
for (roa in _case.collect("roaMessages[message=='Case is Voided' or #p1.contains(id)]", roaToRemove)) {
  logger.debug("mark roa ${roa.entityIdAndTitle} deleted");
  roa.la_deleted = true;
  roa.la_changeDate = new Date();
  roa.saveOrUpdate();
}

if (_docToRestore) {
  for (doc in _case.collect("documents[id==#p1]", _docToRestore)) {
    logger.debug("restore doc ${doc.entityIdAndTitle}");
    doc.dispositionDate = null;
    doc.dispositionType = null;
    doc.updateReason = null;
    for (roa in doc.collect("relatedRoaMessages[la_deleted == true]")) {
      logger.debug("mark roa ${roa.entityIdAndTitle} NOT deleted");
      roa.la_deleted = null;
      roa.la_changeDate = new Date();
      roa.saveOrUpdate();      
    }
  }
}

if (_hearingToRestore) {
  for (hearing in _case.collect("hearings[id==#p1]", _hearingToRestore)) {
      logger.debug("update vacated hearing to active ${hearing.entityIdAndTitle}");
      hearing.resultType = null;
      hearing.resultDate = null;
      hearing.eventStatus = "EVENT";
      hearing.saveOrUpdate();

    for (roa in hearing.collect("relatedRoaMessages[message.toLowerCase().contains('vacate')]")) {
      logger.debug("mark roa ${roa.entityIdAndTitle} deleted");
      roa.la_deleted = true;
      roa.la_changeDate = new Date();
      roa.saveOrUpdate();      
    }
  }
}

if (_caseDispo) {
  logger.debug("set case dispo ${_caseDispo} on ${_caseDispoDate}");
  _case.dispositionType = _caseDispo;
  _case.dispositionDate = _caseDispoDate;
  _case.saveOrUpdate();
} else {
  logger.debug("clear case dispo ${_case}");
  _case.dispositionType = null;
  _case.dispositionDate = null;
  _case.saveOrUpdate();
  
  logger.debug("recalc case dispo ${_case}");
  runRule('CASE_CALCULATE_DISPOSITION', ['case': _case]);
}
logger.debug("final case dispo ${_case.dispositionType}");

if (_case.dispositionDate && _updateFutureHearings) {
  for (hearing in _case.collect("hearings")) {
    if (!DateUtil.isFutureDate(hearing.startDateTime)) {
      continue;
    }
    
    if (hearing.resultType == 'VAC' && hearing.statusCanceled) {
      logger.debug("update vacated hearing to active ${hearing.entityIdAndTitle}");
      hearing.resultType = null;
      hearing.resultDate = null;
      hearing.eventStatus = "EVENT";
      hearing.saveOrUpdate();
    }
  }
}

addMessage("Case ${_case.caseNumber} has been unvoided");






