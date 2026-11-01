#define _GNU_SOURCE
#include <pty.h>
#include <sys/ioctl.h>
#include <stdint.h>
#include <poll.h>
#include <unistd.h>
#include <stdlib.h>
#include <errno.h>
#include <signal.h>
#include <sys/prctl.h>
#include <sys/wait.h>
static int copy_bytes(int src,int dst){
    char buf[8192];
    ssize_t count=read(src,buf,sizeof(buf));
    if(count<=0)return 0;
    for(ssize_t offset=0;offset<count;){
        ssize_t written=write(dst,buf+offset,count-offset);
        if(written<=0)return 0;
        offset+=written;
    }
    return 1;
}
int main(int argc,char **argv){
    if(argc!=4 && argc!=5)return 2;
    struct winsize size={.ws_row=atoi(argv[2]),.ws_col=atoi(argv[3])};
    int master;
    pid_t child=forkpty(&master,NULL,NULL,&size);
    if(child<0)return 1;
    if(child==0){
        prctl(PR_SET_PDEATHSIG,SIGHUP);
        execl(argv[1],"bash","--noprofile","--norc","-i",(char*)NULL);
        _exit(127);
    }
    struct pollfd fds[]={{0,POLLIN,0},{master,POLLIN,0},{3,POLLIN,0}};
    while(poll(fds,argc==5?3:2,-1)>0){
        if(argc==5 && fds[2].revents){
            uint16_t dims[2]; size_t used=0;
            while(used<sizeof(dims)){
                ssize_t n=read(3,(char*)dims+used,sizeof(dims)-used);
                if(n<=0)break;
                used+=n;
            }
            if(used!=sizeof(dims))break;
            size.ws_row=dims[0];size.ws_col=dims[1];
            ioctl(master,TIOCSWINSZ,&size);
        }
        if(fds[0].revents && !copy_bytes(0,master))break;
        if(fds[1].revents && !copy_bytes(master,1))break;
    }
    close(master);
    kill(child,SIGHUP);
    waitpid(child,NULL,0);
    return 0;
}
