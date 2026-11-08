package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.employee.WorkGroupType;

/** 활동별로 특정 직군의 가입을 제한하는 규칙. */
public interface ActivityWorkGroupRestriction {
    ParticipationDecision evaluate(WorkGroupType workGroup, Activity activity);
}
