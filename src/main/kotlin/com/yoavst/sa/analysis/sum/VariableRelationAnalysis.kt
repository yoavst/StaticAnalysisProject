package com.yoavst.sa.analysis.sum

import com.yoavst.sa.analysis.utils.Analysis

interface VariableRelationAnalysis : Analysis<VariableRelations> {
    val variableToIndex: Map<String, Int>
}