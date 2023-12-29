<template>
  <div>
    <div class="common-layout">

      <el-container>

        <el-main>
          <div style="float: contour; font-size: 40px;margin-top: 30px">
            欢迎使用实时用户行为分析平台
          </div>
          <div class="w">
            <div class="k">

              <el-card class="box-card" shadow="always" body-style="border-radius: 20px">
                <div v-for="o in 1" :key="o" class="text item">
                  <div class="s">
                    <el-form :model="ruleForm" status-icon :rules="rules" ref="ruleForm"
                    >
                      <el-form-item label="用户名" prop="username">
                        <el-input type="username" v-model="ruleForm.username" autocomplete="off"></el-input>
                      </el-form-item>

                      <el-form-item label="密&nbsp;&nbsp;&nbsp;码" prop="password">
                        <el-input type="password" v-model="ruleForm.password" autocomplete="off"></el-input>
                      </el-form-item>

                      <el-radio-group v-model="radio">
                        <el-radio :label="3" @click="aa(3)">管理员</el-radio>
                        <el-radio :label="6" @click="aa(6)">普通用户</el-radio>
                        <el-radio @click="aa(9)">设计师</el-radio>
                      </el-radio-group>

                      <el-form-item style="margin-top: 10px">
                        <el-button type="primary" @click="submit">登录</el-button>
                        <el-button  @click="resetForm('ruleForm')">重置</el-button>
                      </el-form-item>

                    </el-form>
                  </div>
                </div>
              </el-card>

            </div>
          </div>


        </el-main>
      </el-container>
    </div>
  </div>
</template>

<script>
  import request from "../utils/request"; //实现http请求，通过相关协议实现前后端的拉通
  import {ElMessage} from 'element-plus'

  export default {
    name: "Login",
    data() {
      var validatePass = (rule, value, callback) => {
        if (value === '') {
          callback(new Error('请输入密码'))
        } else {
          if (this.ruleForm.password !== '') {
            this.$refs.ruleForm.validateField('password')
          }
          callback()
        }
      }
      var validateUserName = (rule, value, callback) => {
        if (value === '') {
          callback(new Error('请输入用户名'))
        } else {
          if (this.ruleForm.username !== '') {
            this.$refs.ruleForm.validateField('username')
          }
          callback()
        }
      }
      return {
        radio: 3,
        routers:'',
        sessione:'',
        url: '',
        ruleForm: {
          password: '',
          username: '',
        },
        rules: {
          password: [{ validator: validatePass, trigger: 'blur' }],
          username: [{ validator: validateUserName, trigger: 'blur' }],
        }
      };
    },
    methods: {

      aa(a) {
        if (a == 3) {
          this.url = '/AdminController/checkLogin'
          this.routers='/index'
          this.sessione='admin'

        }

        if (a == 6) {
          this.url = '/CustomerController/checkLogin'
          this.routers='/index'
          this.sessione='customer'

        }
        if (a == 9) {
          this.url = '/DriverController/checkLogin'
          this.routers='/index'
          this.sessione='driver'

        }


      },

      submit() {
        request.get(this.url, {

          params: {
            'username': this.ruleForm.username,
            'password': this.ruleForm.password
          }
        }).then(res => {
          if (res.code == 0) {
            //登陆成功
            ElMessage.success({
              message: res.message,
              type: 'success'
            });
            //跳转框架页 $router是整个路由对象  $route当前跳转的路由
            this.$router.push(this.routers)
            //vue中提供了使用cookie域和session域的使用
            sessionStorage.setItem(this.sessione, JSON.stringify(res.data))
          } else {
            //登陆失败
            ElMessage.error({
              message: res.message,
              type: 'error'
            });
          }
        })
      }

    },
    created() {
      this.url = '/AdminController/checkLogin'
      this.routers='/index'
      this.sessione='admin'
    }

  }



</script>

<style scoped>
  .el-header, .el-footer {
    background-color: beige;
    color: var(--el-text-color-primary);
    text-align: center;
    line-height: 10px;
  }

  .el-aside {
    background-color: #D3DCE6;
    color: var(--el-text-color-primary);
    text-align: center;
    line-height: 10px;
  }

  .el-main {
    background-image: url("../assets/img/bg.png");
    height: calc(100vh);
    width: 100%;
    background-repeat: round;
    color: var(--el-text-color-primary);
    text-align: center;

  }

  body > .el-container {
    margin-bottom: 10px;
  }

  .el-container:nth-child(5) .el-aside,
  .el-container:nth-child(6) .el-aside {
    line-height: 210px;
  }

  .el-container:nth-child(7) .el-aside {
    line-height: 320px;
  }

  .w {
    width: 100%;
    height: 250px;
    opacity: 0.8;
  }

  .k {

    width: 50%;
    height: 250px;
    margin: 100px auto;
  }

  .item {
    padding: 18px 0;
  }

  .box-card {
    width: 480px;
    margin: 0 auto;
  }

  .s {
    width: 90%;
    margin: 0 auto;
    vertical-align: center;

  }
</style>
