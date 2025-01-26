<template>
    <section class="bg-green-50 px-4 py-10">
      <div class="container-xl lg:container m-auto">
        <h2 class="text-3xl font-bold text-green-500 mb-6 text-center">
          Browse Jobs
        </h2>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-6">

        </div>
      </div>
    </section>    
</template>

<script setup>
import jobData from '@/jobs.json'
import { ref, defineProps } from 'vue';

const jobs = ref(jobData);


</script>