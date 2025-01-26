<template>
  <h1 v-if="status">Vue Jobs</h1>
  <ul>
    <li v-for="task in tasks" :key="task.name">
      name : {{ task.name }}
      age : {{ task.age }}
    </li>
  </ul>
</template>

<script>
export default {
  data() {
    return {
      name: 'Jone, Doe',
      age: 18,
      status: false,
      tasks: [
        {
          name: 'TEST',
          age: 10
        },
        {
          name: 'TA1',
          age: 15
        }
      ]
    };
  },
  methods: {
    toggleStatus() {
      
    }
  }

}
</script>
<style scoped>
h1 {
  color: red;
}
</style>