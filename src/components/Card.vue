<template>
    <div class="bg-gray-100 p-6 rounded-lg shadow-md">
        <slot></slot>
    </div>
</template>
<script setup>
import { defineProps }  from vuePlugin;

defineProps({
    bg: {
        type: String,
        default: 'bg-gray-100',
    }
})
</script>
