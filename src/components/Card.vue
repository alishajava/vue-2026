<template>
    <div class="bg-gray-100 p-6 rounded-lg shadow-md">
        <h2 class="text-2xl font-bold">For Developers</h2>
        <p class="mt-2 mb-4">
            Browse our Vue jobs and start your career today
        </p>
        <a
            href="jobs.html"
            class="inline-block bg-black text-white rounded-lg px-4 py-2 hover:bg-gray-700"
        >
            Browse Jobs
        </a>
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
