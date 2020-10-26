//SUBCASE_VOID
logger.debug("unvoid subCase ${_subCase}");
_case = _subCase.case;
//IR546286 Charlton added on 9/9/2020
//if (_subCase.dispositionType != '2100') {
if (_subCase.dispositionType != '2100' || _subCase.dispositionType !='1300VSC') {
  addError("Sub Case ${_subCase.title} is currently not void");
  return;
}

logger.debug("clear subcase dispo ${_subCase}");
_subCase.dispositionType = null;
_subCase.dispositionDate = null;
_subCase.saveOrUpdate();

logger.debug("clear case dispo ${_case}");
_case.dispositionType = null;
_case.dispositionDate = null;
_case.saveOrUpdate();

logger.debug("recalc case dispo ${_case}");
runRule('CASE_CALCULATE_DISPOSITION', ['case': _case]);
logger.debug("final case dispo ${_case.dispositionType}");

addMessage("Sub Case ${_subCase.title} has been unvoided");



















