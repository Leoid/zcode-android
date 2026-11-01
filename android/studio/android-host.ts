import {createLocalServices,createSettingService,migrateApplicationStorage} from "@zcode/services/node";
import {IProjectService} from "@zcode/services";
import {createHttpServer} from "./packages/server/src/http";
const workspacePath=process.env.ZCODE_SERVER_WORKSPACE!;
const authToken=process.env.ZCODE_SERVER_AUTH_TOKEN;
if(!authToken)throw Error("Missing app authentication");
await migrateApplicationStorage(await createSettingService().get(),"local");
const services=createLocalServices({projectStorageHostId:"local",serviceAuthorityMode:"standalone-server",zcodeBuiltinProviderConfigFilePath:process.env.ZCODE_BUILTIN_PROVIDER_CONFIG_FILE!,runtimeProcessEnvPatch:process.env});
const projects=services.getOptional(IProjectService);
if(projects && !(await projects.readTree()).projects.some(project=>project.roots.some(root=>root.rootPath===workspacePath))){
 await projects.saveProject({roots:[workspacePath],reuseExistingRoot:true});
}
createHttpServer(services,Number(process.env.PORT),{host:"127.0.0.1",serverId:"android-local",authToken,authRequired:true,staticRoot:process.env.ZCODE_WEB_STATIC_ROOT,spaFallback:true,virtualProjects:true});
