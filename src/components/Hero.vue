<template>
    <section class="bg-green-700 py-20 mb-4">
      <div
        class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col items-center"
      >
        <div class="text-center">
          <h1
            class="text-4xl font-extrabold text-white sm:text-5xl md:text-6xl"
          >
            {{ title }} {{ subTitle }}
          </h1>
          <p class="my-4 text-xl text-white">
            Find the Vue job that fits your skills and needs
          </p>
        </div>
      </div>
    </section>
</template>

<script setup>
import { defileProps } from vue;

defileProps({
    title: {
        type: String,
        default: 'TEST'
    },
    subTitle: {
      type: String
    }
})



</script>