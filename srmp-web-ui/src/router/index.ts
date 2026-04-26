import { createRouter, createWebHistory } from 'vue-router'
import OneMap from '../views/gis/OneMap.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/gis/one-map'
    },
    {
      path: '/gis/one-map',
      component: OneMap,
      meta: {
        title: 'GIS 一张图'
      }
    }
  ]
})

export default router