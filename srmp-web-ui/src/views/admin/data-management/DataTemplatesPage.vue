<template>
  <AgentPageShell title="模板与规范" description="下载示例模板，并按顺序完成路网 → 路段 → 病害导入。">
    <el-alert type="warning" show-icon :closable="false" class="mb">
      <strong>导入顺序：</strong>先导入路网 → 再导入路段 → 再导入病害（病害同样依赖路网）。
    </el-alert>
    <el-row :gutter="16">
      <el-col v-for="block in specBlocks" :key="block.key" :xs="24" :lg="8">
        <el-card shadow="never">
          <template #header>{{ block.title }}</template>
          <dl class="spec-dl">
            <dt>支持格式</dt><dd>{{ block.format }}</dd>
            <dt>文件结构</dt><dd>{{ block.structure }}</dd>
            <dt>必填字段</dt><dd>{{ block.requiredFields }}</dd>
            <dt>坐标系</dt><dd>{{ block.crs }}</dd>
            <dt>常见错误</dt><dd>{{ block.commonErrors }}</dd>
          </dl>
          <el-button type="primary" link @click="download(block.templateType)">{{ block.downloadText }}</el-button>
        </el-card>
      </el-col>
    </el-row>
  </AgentPageShell>
</template>

<script setup lang="ts">
import AgentPageShell from '../../agent/components/AgentPageShell.vue'
import { templateDownloadUrl } from '../../../api/dataMgmt'

const specBlocks = [
  { key: 'road', title: '路网', templateType: 'road-network' as const, format: 'Shapefile 模板包（.tar）', structure: '下载 road_template.tar；字段按系统路网导入要求整理', requiredFields: 'ROUTE_NO、NAME_CHN 或 NAME_ENG、几何线', crs: '以模板内 .prj 为准', commonErrors: '缺少 .prj、缺少 ROUTE_NO、多组 shp、路线名称为空', downloadText: '下载路网 SHP 模板包' },
  { key: 'section', title: '路段', templateType: 'section' as const, format: 'Shapefile 模板包（.tar）', structure: '下载 road_section_template.tar；包含系统需要的路段 Shapefile 模板', requiredFields: 'linkCode、startMp、endMp、upDown、year、四级编码字段', crs: '以模板内 .prj 为准；须先完成路网导入', commonErrors: '未先导入路网、linkCode 无法匹配路线、缺少四级 shp、缺少 .prj', downloadText: '下载路段 SHP 模板包' },
  { key: 'disease', title: '病害', templateType: 'disease' as const, format: '.xlsx / .csv 表格模板', structure: '病害台账表', requiredFields: '病害类型、位置、关联路线', crs: '点位可关联路网', commonErrors: '路线未匹配、坐标为空', downloadText: '下载病害表格模板' }
]

function download(type: 'road-network' | 'section' | 'disease') {
  window.open(templateDownloadUrl(type), '_blank')
}
</script>

<style scoped>
.mb { margin-bottom: 16px; }
.spec-dl { margin: 0 0 12px; font-size: 13px; }
.spec-dl dt { color: #64748b; margin-top: 8px; }
.spec-dl dd { margin: 4px 0 0; color: #334155; }
</style>
